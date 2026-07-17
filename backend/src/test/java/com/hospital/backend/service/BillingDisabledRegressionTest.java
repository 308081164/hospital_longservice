package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INT-03：关闭特色开关时回退标准计价。
 */
class BillingDisabledRegressionTest {

    @Test
    void billingDisabledUsesStandardPricingWithoutSpecialRules() throws Exception {
        JsonNode compiled = JsonUtils.getObjectMapper().valueToTree(DefaultPricingTemplate.buildRulesMap());

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "type", "额外包(纸塑袋)",
                "packName", "普通器械-2/Z7526",
                "packageMaterial", "高温纸塑袋20cm",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 0.0,
                "totalPrice", 0.0,
                "hospitalName", "东北农业大学医院"));

        assertThat(result.matchedRuleId).isNull();
        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void billingEnabledAppliesCompiledSpecialRules() throws Exception {
        JsonNode compiled = JsonUtils.getObjectMapper().readTree("""
                {
                  "billingEnabled": true,
                  "customerOverrides": {},
                  "specialRules": {
                    "fixedPrices": [{
                      "ruleId": 1,
                      "name": "测试固定价",
                      "price": 88,
                      "keywords": ["测试包"],
                      "skipPackaging": true,
                      "skipDiscount": true
                    }]
                  }
                }
                """);
        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "type", "器械包",
                "packName", "测试包-A",
                "packageMaterial", "纸塑袋",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0,
                "hospitalName", "测试医院"));

        assertThat(result.expectedUnitPrice).isEqualTo(88.0);
        assertThat(result.matchedRuleId).isEqualTo(1L);
    }
}
