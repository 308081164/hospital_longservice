package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.service.BillingConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RulesVerificationServiceImplTest {

    @Test
    void acceptedTypesNormalizeMatchesImportPath() throws Exception {
        JsonNode rule = JsonUtils.getObjectMapper().readTree("""
                {
                  "ruleType": "EXTRA_FEE",
                  "name": "妇幼人口0度膀胱镜加收8",
                  "fee": 8.0,
                  "acceptedTypes": ["单包装（低温老肯）"]
                }
                """);
        String viaImport = BillingConditionEvaluator.mergeAcceptedTypesIntoConditions(
                null, rule.get("acceptedTypes"));
        String viaVerify = RulesVerificationServiceImpl.resolveConditionsJsonFromJson(rule);
        assertThat(viaVerify).isEqualTo(viaImport);
    }

    @Test
    void effectivePriceUsesFeeWhenPriceMissing() throws Exception {
        JsonNode rule = JsonUtils.getObjectMapper().readTree("""
                {"ruleType":"EXTRA_FEE","fee":8.0}
                """);
        assertThat(RulesVerificationServiceImpl.effectivePriceFromJson(rule))
                .isEqualByComparingTo(BigDecimal.valueOf(8.0));
    }

    @Test
    void entityEffectivePriceFallsBackToFee() {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setRuleType("EXTRA_FEE");
        rule.setFee(BigDecimal.valueOf(8));
        assertThat(RulesVerificationServiceImpl.effectivePriceFromEntity(rule))
                .isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    void keywordMatchModeTreatsExactTokenAsUnspecified() {
        assertThat(RulesVerificationServiceImpl.normalizeKeywordMatchMode(null)).isNull();
        assertThat(RulesVerificationServiceImpl.normalizeKeywordMatchMode("exact_token")).isNull();
        assertThat(RulesVerificationServiceImpl.normalizeKeywordMatchMode("contains")).isEqualTo("contains");
    }

    @Test
    void conditionsJsonCanonicalizationIgnoresKeyOrder() throws Exception {
        JsonNode rule = JsonUtils.getObjectMapper().readTree("""
                {
                  "ruleType": "PRICE_PER_INSTRUMENT",
                  "price": 5.5,
                  "acceptedTypes": ["额外包（纸塑袋）"],
                  "conditionsJson": "[{\\"field\\":\\"manualReview\\",\\"value\\":true}]"
                }
                """);
        String viaVerify = RulesVerificationServiceImpl.resolveConditionsJsonFromJson(rule);
        String dbStored = "[{\"field\": \"manualReview\", \"value\": true}, "
                + "{\"field\": \"type\", \"value\": [\"额外包（纸塑袋）\"], \"operator\": \"in\"}]";
        assertThat(JsonUtils.canonicalJsonText(viaVerify))
                .isEqualTo(JsonUtils.canonicalJsonText(dbStored));
    }
}
