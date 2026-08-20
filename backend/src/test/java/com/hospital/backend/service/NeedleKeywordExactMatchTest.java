package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 小件关键词「精准 token 匹配」验收：
 * 对照 特殊收费(13).xlsx「通用特殊收费」及 UI 识别关键词列表。
 */
class NeedleKeywordExactMatchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest(name = "命中 {1} → {0}")
    @CsvSource({
            "车针-32/Z7520, 车针",
            "拔髓针-10/z7537, 拔髓针",
            "根管针-5/z7534, 根管针",
            "根管锉-8/z7534, 根管锉",
            "缝合针-1/W6050, 缝合针",
            "探针-1/Z1026, 探针",
            "穿刺针-3/z7534, 穿刺针",
            "手术针-2/z7520, 手术针",
            "小件-1/Z7526, 小件",
            "卷棉子-6/z7534, 卷棉子",
            "车针/Z7520, 车针",
            "挖勺-4/Z7526, 挖勺",
            "支抗钉-2/z7534, 支抗钉",
            "洁牙机尖-3/z7534, 洁牙机尖",
            "球钻-1/z7534, 球钻",
            "成型片-5/z7534, 成型片",
            "针-5/z7534, 针",
    })
    void exactTokenMatch_positiveCasesFromExcelAndUi(String packName, String keyword) {
        assertThat(BillingConditionEvaluator.matchesKeywordExactToken(packName, keyword)).isTrue();
    }

    @ParameterizedTest(name = "不命中 {1} → {0}")
    @CsvSource({
            "克氏针-3/z7534, 针",
            "克氏针-3/z7534, 车针",
            "银质针-6/z7534, 针",
            "银质针-6/z7534, 拔髓针",
            "内热针-4/z7534, 针",
            "扩大针-5/z7534, 针",
            "根扩针-3/z7534, 根管针",
            "指针-10/z7537, 针",
            "正畸去胶车针-1/Z7520, 车针",
            "泪道探针测试包, 探针",
            "小件盒-1/Z7526, 小件",
            "抛光车针盒6件盒1/Z1026, 车针",
            "刮勺探针4/z1035, 探针",
            "克氏针折弯钳-1/W6050, 针",
            "大车针盒-1/Z1526, 车针",
            "牙探针-2/z7534, 探针",
    })
    void exactTokenMatch_negativeEmbeddedSubstringCases(String packName, String keyword) {
        assertThat(BillingConditionEvaluator.matchesKeywordExactToken(packName, keyword)).isFalse();
    }

    @ParameterizedTest(name = "默认关键词列表命中 → {0}")
    @CsvSource({
            "车针-2/z7534",
            "拔髓针-10/z7537",
            "根管锉-12/z7534",
            "挖勺-3/Z7530",
            "支抗钉-1/z7534",
    })
    void defaultNeedleKeywordList_matchesPackNameAsExactToken(String packName) throws Exception {
        assertThat(BillingConditionEvaluator.matchesKeywordsExactToken(packName, defaultNeedleKeywords()))
                .isTrue();
    }

    @ParameterizedTest(name = "默认关键词列表不命中 → {0}")
    @CsvSource({
            "银质针-8/z7534",
            "指针-10/z7537",
            "正畸去胶车针-1/Z7520",
            "小件盒-1/Z7526",
    })
    void defaultNeedleKeywordList_rejectsEmbeddedSubstring(String packName) throws Exception {
        assertThat(BillingConditionEvaluator.matchesKeywordsExactToken(packName, defaultNeedleKeywords()))
                .isFalse();
    }

    @Test
    void mixedPackName_stillMatchesLeadingSmallItemKeyword() throws Exception {
        BillingConditionEvaluator.ExactTokenKeywordMatch match =
                BillingConditionEvaluator.findLongestExactTokenKeyword(
                        "探针1窥器1宫颈钳1/z2044", defaultNeedleKeywords());
        assertThat(match).isNotNull();
        assertThat(match.keyword()).isEqualTo("探针");
    }

    @Test
    void preferLongerKeyword_chuanCiZhenOverDanZiZhen() throws Exception {
        assertThat(BillingConditionEvaluator.matchesKeywordExactToken("穿刺针-3/z7534", "针")).isFalse();
        assertThat(BillingConditionEvaluator.matchesKeywordExactToken("穿刺针-3/z7534", "穿刺针")).isTrue();

        BillingConditionEvaluator.ExactTokenKeywordMatch match =
                BillingConditionEvaluator.findLongestExactTokenKeyword("穿刺针-3/z7534", defaultNeedleKeywords());
        assertThat(match).isNotNull();
        assertThat(match.keyword()).isEqualTo("穿刺针");
    }

    @Test
    void pricingEngine_generalCheZhenFoldFromExcel() throws Exception {
        JsonNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(rules);
        // 通用特殊收费「车针」走模板 FOLD（非 needle 关键词路径）
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "车针-2/z7534",
                "packageMaterial", "高温纸塑袋75*370",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 8.0,
                "totalPrice", 8.0));

        assertThat(result.notes).anyMatch(n -> n.contains("通用小件5合1含包材"));
    }

    @Test
    void pricingEngine_embeddedCheZhenInOrthodonticName_doesNotApplyGlobalSmallItemFold() throws Exception {
        JsonNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨市第二医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "正畸去胶车针-1/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 8.0,
                "totalPrice", 8.0));

        assertThat(result.notes).noneMatch(n -> n.contains("名称含小件关键词"));
    }

    @Test
    void pricingEngine_kirschnerWire_hitsGlobalFold_notNeedleKeyword() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "骨科",
                "type", "额外包(纸塑袋)",
                "packName", "克氏针-12/z7534",
                "packageMaterial", "高温纸塑袋75*300",
                "instrumentCount", 12,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5));

        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.pricingRule).isEqualTo("通用小件5合1免包材");
        assertThat(result.notes).noneMatch(n -> n.contains("名称含小件关键词"));
        assertThat(result.notes).anyMatch(n -> n.contains("通用小件5合1免包材"));
    }

    @Test
    void pricingEngine_waShaoFoldWhenExactTokenMatches() throws Exception {
        JsonNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "挖勺-5/Z7530",
                "packageMaterial", "高温纸塑袋75*300",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 19.25,
                "totalPrice", 19.25));

        assertThat(result.notes).anyMatch(n -> n.contains("名称含小件关键词"));
    }

    @Test
    void pricingEngine_overTenProbeSkipsBagFeeViaExactMatch() throws Exception {
        JsonNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "探针-12/z7534",
                "packageMaterial", "高温纸塑袋75*370",
                "instrumentCount", 12,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5));

        assertThat(result.notes).anyMatch(n -> n.contains("小件器械超过 10 件，按客户标准不加袋子钱"));
    }

    @Test
    void pricingEngine_embeddedCheZhenOverTen_doesNotSkipBagFee() throws Exception {
        JsonNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试医院",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "正畸去胶车针-12/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 12,
                "packCount", 1,
                "unitPrice", 66.0,
                "totalPrice", 66.0));

        assertThat(result.notes).noneMatch(n -> n.contains("小件器械超过 10 件，按客户标准不加袋子钱"));
    }

    @Test
    void unmatchedAnalyzer_usesExactTokenMatch() {
        UnmatchedProductAnalyzer analyzer = new UnmatchedProductAnalyzer();
        UnmatchedProductAnalyzer.Suggestion embedded = analyzer.analyze(
                "正畸去胶车针-1/Z7520", "额外包(纸塑袋)", "", analyzer.defaultNeedleKeywords());
        assertThat(embedded.likelySmallItem()).isFalse();
        assertThat(embedded.matchedNeedleKeywords()).isEmpty();

        UnmatchedProductAnalyzer.Suggestion exact = analyzer.analyze(
                "车针-10/z7534", "额外包(纸塑袋)", "", analyzer.defaultNeedleKeywords());
        assertThat(exact.likelySmallItem()).isTrue();
        assertThat(exact.matchedNeedleKeywords()).contains("车针");
    }

    private static JsonNode defaultNeedleKeywords() throws Exception {
        return MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()).path("needle").path("keywords");
    }
}
