package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.BillExportPriceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 附一 7 月 export parity：人工核对版 ground truth，单价/总价零容差。
 */
class ZyyD1JulyExportParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PricingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = new PricingEngine(buildZyyD1Rules());
    }

    @Test
    void exportPricesMatchJulyManualGroundTruth() throws Exception {
        JsonNode cases = MAPPER.readTree(getClass().getResourceAsStream(
                "/zyy-d1-july-export-parity-cases.json"));
        for (JsonNode node : cases) {
            Map<String, Object> row = toRow(node);
            PricingEngine.ProcessedResult priced = engine.processRow(row);

            BillRowItem exportRow = new BillRowItem();
            exportRow.setPackCount(intVal(node, "packCount"));
            exportRow.setInstrumentCount(intVal(node, "instrumentCount"));
            exportRow.setUnitPrice(dbl(node, "unitPrice"));
            exportRow.setTotalPrice(dbl(node, "totalPrice"));
            exportRow.setExpectedUnitPrice(priced.expectedUnitPrice);
            exportRow.setCorrectedTotalPrice(priced.correctedTotalPrice);

            double expectedUnit = node.path("expectedExportUnit").asDouble();
            double expectedTotal = node.path("expectedExportTotal").asDouble();
            String label = node.path("label").asText();

            assertThat(BillExportPriceResolver.resolveUnitPrice(exportRow))
                    .as(label + " unit")
                    .isCloseTo(expectedUnit, offset(0.001));
            assertThat(BillExportPriceResolver.resolveTotalPrice(exportRow))
                    .as(label + " total")
                    .isCloseTo(expectedTotal, offset(0.001));
        }
    }

    private static ObjectNode buildZyyD1Rules() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("pricingMode", "hybrid");
        billingProfile.put("enabled", true);

        JsonNode stdSeed = MAPPER.readTree(ZyyD1P0PricingRegressionTest.class.getResourceAsStream(
                "/billing-seeds/phase-zyy-d1-standard-pricing-20260723.json"));
        deepMerge(rules, (ObjectNode) stdSeed.path("customerUpdates").get(0).path("standardPricingOverride"));

        ObjectNode specialRules = (ObjectNode) rules.get("specialRules");
        ArrayNode fixedPrices = (ArrayNode) specialRules.get("fixedPrices");
        ArrayNode foldRules = (ArrayNode) specialRules.get("foldRules");

        JsonNode fuyiSeed = MAPPER.readTree(ZyyD1P0PricingRegressionTest.class.getResourceAsStream(
                "/billing-seeds/phase-zyy-d1-fuyi.json"));
        for (JsonNode node : fuyiSeed.path("profiles").get(0).path("productRules")) {
            ObjectNode compiled = (ObjectNode) node.deepCopy();
            if ("FOLD".equals(node.path("ruleType").asText("FIXED_PRICE"))) {
                foldRules.add(compiled);
            } else {
                if ("PRICE_PER_INSTRUMENT".equals(node.path("ruleType").asText())) {
                    compiled.put("pricePerInstrument", true);
                }
                fixedPrices.insert(0, compiled);
            }
        }

        applyRuleUpdate(fixedPrices, "30°腹腔镜组合价", 30.38);
        applyRuleUpdate(fixedPrices, "30度腹腔镜组合价golden30.4", 30.38);
        applyRuleUpdate(fixedPrices, "辅料包整包价", 27.97);
        applyRuleUpdate(fixedPrices, "孔巾包整包价", 27.97);
        applyRuleUpdate(fixedPrices, "腔镜包整包价", 27.97);

        JsonNode foldFix = MAPPER.readTree(ZyyD1P0PricingRegressionTest.class.getResourceAsStream(
                "/billing-seeds/phase-zyy-d1-fold-ganlan-chongxi-fix-20260728.json"));
        for (JsonNode update : foldFix.path("ruleUpdates")) {
            patchFoldRule(foldRules, update.path("ruleName").asText(),
                    update.path("setFoldRatio").asInt(), update.path("setThreshold").asInt());
        }
        return rules;
    }

    private static void applyRuleUpdate(ArrayNode fixedPrices, String ruleName, double price) {
        for (JsonNode rule : fixedPrices) {
            if (ruleName.equals(rule.path("name").asText())) {
                ((ObjectNode) rule).put("price", price);
                return;
            }
        }
    }

    private static void patchFoldRule(ArrayNode foldRules, String ruleName, int foldRatio, int threshold) {
        for (JsonNode rule : foldRules) {
            if (ruleName.equals(rule.path("name").asText())) {
                ((ObjectNode) rule).put("foldRatio", foldRatio);
                ((ObjectNode) rule).put("threshold", threshold);
                return;
            }
        }
    }

    private static void deepMerge(ObjectNode target, ObjectNode patch) {
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode patchVal = entry.getValue();
            if (patchVal.isObject()) {
                JsonNode existing = target.get(key);
                if (existing instanceof ObjectNode existingObj) {
                    deepMerge(existingObj, (ObjectNode) patchVal);
                } else {
                    target.set(key, patchVal.deepCopy());
                }
            } else {
                target.set(key, patchVal.deepCopy());
            }
        });
    }

    private static Map<String, Object> toRow(JsonNode node) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "黑龙江中医药大学附属第一医院");
        row.put("packName", node.path("packName").asText());
        row.put("type", node.path("type").asText());
        row.put("packageMaterial", node.path("packageMaterial").asText());
        row.put("instrumentCount", intVal(node, "instrumentCount"));
        row.put("packCount", intVal(node, "packCount"));
        row.put("unitPrice", dbl(node, "unitPrice"));
        row.put("totalPrice", dbl(node, "totalPrice"));
        if (node.has("department")) {
            row.put("department", node.path("department").asText());
        }
        return row;
    }

    private static int intVal(JsonNode node, String field) {
        return node.path(field).asInt(1);
    }

    private static double dbl(JsonNode node, String field) {
        return node.path(field).asDouble();
    }
}
