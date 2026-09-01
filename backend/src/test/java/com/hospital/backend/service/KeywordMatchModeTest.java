package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 小件计价关键词匹配模式（exact_token / contains）验收。
 * 固定价/加收类规则：缺省 contains（对应特殊收费 Excel「包名称带X」语义，
 *           与 2026-08-27 基线的组合文本包含行为一致）。
 * 折算（FOLD）规则：缺省 exact_token（2026-08-27 基线行为：包名分词匹配，
 *           "针"类模式词命中"针5盒1"而不误伤"车针排/车针盒"等长名称）；
 *           规则显式 contains 或关键词带 @contains 后缀时走包含。
 * exact_token：名称严格对应，仅对包名做精确 token 边界匹配。
 */
class KeywordMatchModeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void resolveKeywordMatchMode_defaultsToContains() {
        assertThat(BillingConditionEvaluator.resolveKeywordMatchMode(MAPPER.createObjectNode()))
                .isEqualTo(BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS);
        assertThat(BillingConditionEvaluator.resolveKeywordMatchMode(null))
                .isEqualTo(BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS);
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("keywordMatchMode", "unknown");
        assertThat(BillingConditionEvaluator.resolveKeywordMatchMode(rule))
                .isEqualTo(BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS);
    }

    @Test
    void matchesKeywordsByMode_contains_triggersOnEmbeddedSubstring() {
        JsonNode keywords = MAPPER.valueToTree(new String[]{"车针"});
        // exact_token 下「车针组件」不命中（车针后紧跟中文字符「组」）
        assertThat(BillingConditionEvaluator.matchesKeywordsByMode(
                "车针组件-5/个 z7520", keywords, BillingConditionEvaluator.KEYWORD_MATCH_EXACT_TOKEN))
                .isFalse();
        // contains 下「车针组件」含「车针」即可命中
        assertThat(BillingConditionEvaluator.matchesKeywordsByMode(
                "车针组件-5/个 z7520", keywords, BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS))
                .isTrue();
    }

    @Test
    void parseKeywordList_splitsAndResolvesSuffixOverrides() {
        assertThat(BillingConditionEvaluator.parseKeywordList("车针@contains,克氏针,银质针@exact"))
                .containsExactly(
                        new BillingConditionEvaluator.ParsedKeyword("车针", "contains"),
                        new BillingConditionEvaluator.ParsedKeyword("克氏针", null),
                        new BillingConditionEvaluator.ParsedKeyword("银质针", "exact_token"));
        // 中文逗号与空格同样生效
        assertThat(BillingConditionEvaluator.parseKeywordList("车针@exact_token，探针"))
                .containsExactly(
                        new BillingConditionEvaluator.ParsedKeyword("车针", "exact_token"),
                        new BillingConditionEvaluator.ParsedKeyword("探针", null));
        // 未识别后缀原样保留，不误伤含 @ 的普通关键词
        assertThat(BillingConditionEvaluator.parseKeywordList("包@2层"))
                .containsExactly(new BillingConditionEvaluator.ParsedKeyword("包@2层", null));
    }

    @Test
    void matchesKeywordsByMode_wordLevelOverride_beatsDefaultMode() {
        // 规则默认 exact_token：车针必须严格对齐，克氏针也必须严格对齐
        JsonNode keywords = MAPPER.valueToTree(new String[]{"车针@contains", "克氏针"});
        // 车针@contains：车针组件（车针后紧跟「组」）应命中
        assertThat(BillingConditionEvaluator.matchesKeywordsByMode(
                "车针组件-5/个 z7520", keywords, BillingConditionEvaluator.KEYWORD_MATCH_EXACT_TOKEN))
                .isTrue();
        // 克氏针无后缀沿用 exact_token：克氏针折弯钳不应命中
        assertThat(BillingConditionEvaluator.matchesKeywordsByMode(
                "克氏针折弯钳-1/W6050", keywords, BillingConditionEvaluator.KEYWORD_MATCH_EXACT_TOKEN))
                .isFalse();
        // 车针@contains 覆盖：即便默认 contains，克氏针仍精确对齐
        JsonNode keywords2 = MAPPER.valueToTree(new String[]{"车针@exact", "克氏针"});
        assertThat(BillingConditionEvaluator.matchesKeywordsByMode(
                "车针组件-5/个 z7520", keywords2, BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS))
                .isFalse();
    }

    @Test
    void findKeywordByMode_contains_returnsPosition() {
        JsonNode keywords = MAPPER.valueToTree(new String[]{"车针", "根管针"});
        BillingConditionEvaluator.ExactTokenKeywordMatch match = BillingConditionEvaluator.findKeywordByMode(
                "车针组件-5/个 z7520", keywords, BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS);
        assertThat(match).isNotNull();
        assertThat(match.keyword()).isEqualTo("车针");
        assertThat(match.position()).isEqualTo(0);
    }

    @Test
    void pricingEngine_genericFoldRule_defaultsExactToken_skipsCheZhenComponent() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "车针组件-5/个 z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 5,
                "packCount", 1,
                "unitPrice", 8.0,
                "totalPrice", 8.0));

        // 折算规则缺省 exact_token（0827 基线）：车针组件（车针后紧跟「组」）不命中通用小件5合1
        assertThat(result.notes).noneMatch(n -> n.contains("通用小件5合1"));
    }

    @Test
    void pricingEngine_customerFoldRule_hulanCottonNeedle_matchesContainsKeyword() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true).put("pricingMode", "hybrid");
        ArrayNode foldRules = rules.with("specialRules").withArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "呼兰一院棉花针5合1含包材");
        fold.put("threshold", 5);
        fold.put("foldRatio", 5);
        fold.put("maxInstrumentCount", 10);
        fold.putArray("keywords").add("棉花针@contains");
        fold.putArray("hospitals").add("呼兰区第一人民医院");

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨市呼兰区第一人民医院",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "棉花针",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 6,
                "packCount", 1,
                "unitPrice", 11.0,
                "totalPrice", 11.0));

        assertThat(result.notes).anyMatch(n -> n.contains("呼兰一院棉花针5合1含包材"));
        assertThat(result.notes).noneMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void pricingEngine_genericFoldKeywords_exactTokenPerBaseline() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "手术室",
                "type", "额外包(纸塑袋)",
                "packName", "克氏针折弯钳-1/W6050",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 8.0,
                "totalPrice", 8.0));

        // 折算规则缺省 exact_token（0827 基线）：克氏针折弯钳（克氏针后紧跟「折」）不命中通用小件5合1
        assertThat(result.notes).noneMatch(n -> n.contains("通用小件5合1"));
    }
}
