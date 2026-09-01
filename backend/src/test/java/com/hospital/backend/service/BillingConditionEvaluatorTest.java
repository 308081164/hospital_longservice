package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingConditionEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void variantRuleRequiresExactVariantMatch() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("variantId", 42L);
        rule.put("productId", 10L);

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 42L, "测试包", "测试包")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 41L, "测试包", "测试包")).isFalse();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "测试包", "测试包")).isFalse();
    }

    @Test
    void productRuleMatchesByProductIdOrKeywords() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("productId", 10L);
        rule.putArray("keywords").add("腹腔镜");

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "任意文本", "任意文本")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "腹腔镜", "额外包-腹腔镜-高温")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "无关包名", "无关包名")).isFalse();
    }

    @Test
    void exactTokenRuleMatchesOnPackNameOnly() throws Exception {
        // 名称严格对应（exact_token）：仅对包名判定，组合文本中袋型紧邻包名不应导致失配
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("keywordMatchMode", "exact_token");
        rule.putArray("keywords").add("大车针盒-1/Z1526");

        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "大车针盒-1/Z1526", "额外包(纸塑袋) 大车针盒-1/Z1526 高温纸塑袋75*200"))
                .isTrue();
        // 包名不完全对应（右侧邻接中文）则不命中
        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "大车针盒-1/Z1526改", "额外包(纸塑袋) 大车针盒-1/Z1526改 高温纸塑袋75*200"))
                .isFalse();
    }

    @Test
    void containsRuleMatchesOnCombinedText() throws Exception {
        // 包含（默认）：在组合文本上做子串包含，与 2026-08-27 基线行为一致
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("环钻包");

        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "环钻包", "器械包 环钻包 无纺布-90×90-50g"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(
                rule, null, null, "无关包", "器械包 无关包 无纺布-90×90-50g"))
                .isFalse();
    }

    @Test
    void matchSignatureDetectsDuplicateRules() {
        var rule1 = java.util.Map.<String, Object>of(
                "ruleType", "FIXED_PRICE",
                "productId", 1,
                "keywords", "[\"test\"]");
        var rule2 = java.util.Map.<String, Object>of(
                "ruleType", "FIXED_PRICE",
                "productId", 1,
                "keywords", "[\"test\"]");

        assertThat(BillingConditionEvaluator.matchSignature(rule1))
                .isEqualTo(BillingConditionEvaluator.matchSignature(rule2));
    }

    @Test
    void matchesRuleKeywords_honorsContainsSuffixInKeyword() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.putArray("keywords").add("棉花针@contains");

        assertThat(BillingConditionEvaluator.matchesRuleKeywords(rule, "额外包(纸塑袋) 棉花针 高温纸塑袋75*200"))
                .isTrue();
        assertThat(BillingConditionEvaluator.matchesRuleKeywords(rule, "额外包(纸塑袋) 无关包 高温纸塑袋75*200"))
                .isFalse();
    }

    @Test
    void temperatureScopeMatchesHtLtAny() {
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("ANY", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "LT")).isFalse();
    }
}
