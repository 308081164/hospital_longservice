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

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 42L, "测试包")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, 41L, "测试包")).isFalse();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "测试包")).isFalse();
    }

    @Test
    void productRuleMatchesByProductIdOrKeywords() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("productId", 10L);
        rule.putArray("keywords").add("腹腔镜");

        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, 10L, null, "任意文本")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "含腹腔镜包")).isTrue();
        assertThat(BillingConditionEvaluator.matchesProductBinding(rule, null, null, "无关包名")).isFalse();
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
    void temperatureScopeMatchesHtLtAny() {
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("ANY", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "HT")).isTrue();
        assertThat(BillingConditionEvaluator.temperatureScopeMatches("HT", "LT")).isFalse();
    }
}
