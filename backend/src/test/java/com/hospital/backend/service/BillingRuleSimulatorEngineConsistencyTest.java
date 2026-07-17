package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P8-14：黄金样例子集 — 引擎试算与 PricingEngine 一致。
 */
class BillingRuleSimulatorEngineConsistencyTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    @Test
    void goldenSubsetMatchesEngineForKnownCases() throws Exception {
        JsonNode root = loadGoldenRows();
        int verified = 0;
        for (JsonNode caseNode : root.path("cases")) {
            if (caseNode.path("rulesOverlay").isObject() && !caseNode.path("rulesOverlay").isEmpty()) {
                continue;
            }
            String caseId = caseNode.path("id").asText();
            if (!caseId.startsWith("dongbei-nongda-ht-")) {
                continue;
            }
            JsonNode rules = buildRulesForGoldenCase(caseNode);
            PricingEngine engine = new PricingEngine(rules);
            Map<String, Object> input = toInput(caseNode.path("input"));
            PricingEngine.ProcessedResult result = engine.processRow(input);
            JsonNode expected = caseNode.path("expected");
            assertThat(result.expectedUnitPrice)
                    .as("case %s", caseId)
                    .isEqualTo(expected.path("expectedUnitPrice").asDouble());
            assertThat(result.status).isEqualTo(expected.path("status").asText());
            verified++;
            if (verified >= 5) {
                break;
            }
        }
        assertThat(verified).isGreaterThanOrEqualTo(3);
    }

    private static JsonNode loadGoldenRows() throws Exception {
        InputStream stream = BillingRuleSimulatorEngineConsistencyTest.class
                .getResourceAsStream("/hospital-billing-golden-rows.json");
        assertThat(stream).isNotNull();
        return MAPPER.readTree(stream);
    }

    private static JsonNode buildRulesForGoldenCase(JsonNode caseNode) {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        JsonNode overlay = caseNode.path("rulesOverlay");
        if (overlay.isObject() && !overlay.isEmpty()) {
            ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
            mergeArray(specialRules, "fixedPrices", overlay.path("fixedPrices"));
            if (overlay.has("billingPolicies")) {
                rules.set("billingPolicies", overlay.path("billingPolicies").deepCopy());
            }
            if (overlay.has("billingProfile")) {
                rules.set("billingProfile", overlay.path("billingProfile").deepCopy());
            }
        }
        return rules;
    }

    private static void mergeArray(ObjectNode target, String field, JsonNode overlay) {
        if (overlay == null || !overlay.isArray() || overlay.isEmpty()) {
            return;
        }
        target.set(field, overlay.deepCopy());
    }

    private static Map<String, Object> toInput(JsonNode input) {
        Map<String, Object> row = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            row.put(entry.getKey(), value.isNumber() ? value.numberValue() : value.asText());
        }
        return row;
    }
}
