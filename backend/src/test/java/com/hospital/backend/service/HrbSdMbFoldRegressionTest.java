package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 松电慢病（HRB-SD-MB）Excel 仅规定「包名称带机扩针」走 5 合 1；
 * 根管锉/车针等不得误命中客户 FOLD 规则（2026-09-02 种子曾误并入 8 个通用小件词）。
 */
class HrbSdMbFoldRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOSPITAL = "哈尔滨道外区松电慢性病专科门诊部";

    @Test
    void rootCanalFileDoesNotHitMachineExpansionNeedleFold() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "额外包(纸塑袋)",
                "根管锉-20件/Z7520",
                "高温纸塑袋75*200",
                20, 1, 66.0, 66.0));

        assertThat(result.notes).noneMatch(n -> n.contains("松电机扩针"));
    }

    @Test
    void burDoesNotHitMachineExpansionNeedleFold() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "额外包(纸塑袋)",
                "车针-20件/Z7520",
                "高温纸塑袋75*200",
                20, 1, 66.0, 66.0));

        assertThat(result.notes).noneMatch(n -> n.contains("松电机扩针"));
    }

    @Test
    void machineExpansionNeedleStillFolds() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "额外包(纸塑袋)",
                "机扩针-20/Z7520",
                "高温纸塑袋75*200",
                20, 1, 22.0, 22.0));

        assertThat(result.notes).anyMatch(n -> n.contains("松电机扩针"));
    }

    private static PricingEngine engineFromBaseline() throws Exception {
        try (InputStream in = HrbSdMbFoldRegressionTest.class.getResourceAsStream(
                "/billing-rules/baseline/HRB-SD-MB.json")) {
            if (in == null) {
                throw new IllegalStateException("HRB-SD-MB baseline missing");
            }
            JsonNode baseline = MAPPER.readTree(in);
            return engineWithFoldRules(baseline.path("productRules"), false);
        }
    }

    private static PricingEngine engineWithFoldRules(JsonNode productRules, boolean stripGenericFolds) throws Exception {
        ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", true);
        billingProfile.put("pricingMode", "hybrid");

        ObjectNode special = (ObjectNode) rules.get("specialRules");
        ArrayNode customerFolds = MAPPER.createArrayNode();
        for (JsonNode rule : productRules) {
            if (!"FOLD".equals(rule.path("ruleType").asText())) {
                continue;
            }
            ObjectNode fold = MAPPER.createObjectNode();
            fold.put("name", rule.path("name").asText());
            fold.put("priority", rule.path("priority").asInt(100));
            fold.put("threshold", rule.path("threshold").asInt(5));
            fold.put("foldRatio", rule.path("foldRatio").asDouble(5));
            if (rule.has("minInstrumentCount")) {
                fold.put("minInstrumentCount", rule.path("minInstrumentCount").asInt());
            }
            if (rule.has("maxInstrumentCount")) {
                fold.put("maxInstrumentCount", rule.path("maxInstrumentCount").asInt());
            }
            fold.put("skipPackaging", rule.path("skipPackaging").asBoolean(false));
            if (rule.has("keywordMatchMode")) {
                fold.put("keywordMatchMode", rule.path("keywordMatchMode").asText());
            }
            fold.set("keywords", rule.path("keywords"));
            fold.set("acceptedTypes", rule.path("acceptedTypes"));
            fold.putArray("hospitals").add(HOSPITAL);
            customerFolds.add(fold);
        }

        ArrayNode mergedFolds = MAPPER.createArrayNode();
        mergedFolds.addAll(customerFolds);
        if (!stripGenericFolds) {
            mergedFolds.addAll((ArrayNode) special.path("foldRules"));
        }
        special.set("foldRules", mergedFolds);
        return new PricingEngine(rules);
    }

    private static Map<String, Object> row(
            String type, String packName, String material,
            int instrumentCount, int packCount,
            double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("department", "手术室");
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", material);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }
}
