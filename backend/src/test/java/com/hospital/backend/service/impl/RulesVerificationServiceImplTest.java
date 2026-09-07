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
}
