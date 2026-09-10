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

/** 人口医院 Excel 28 条规则回归（不含已删除的 5 条冗余规则）。 */
class HljFyRkExcelRulesRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOSPITAL = "黑龙江省妇幼保健院（人口）";

    @Test
    void padOnLowTempHitsSealGroupNotPaperPlasticFold() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "额外包(低温等离子)",
                "垫片-4/Z7520",
                "低温纸塑袋20cm",
                4, 1, 22.0, 22.0));

        assertThat(result.pricingRule).contains("垫片");
        assertThat(result.pricingRule).doesNotContain("密封件");
    }

    @Test
    void thirtyDegreeBladderRuleMatchesBillRow() throws Exception {
        try (InputStream in = HljFyRkExcelRulesRegressionTest.class.getResourceAsStream(
                "/billing-rules/baseline/HLJ-FY-RK.json")) {
            JsonNode baseline = MAPPER.readTree(in);
            JsonNode thirtyRule = null;
            for (JsonNode rule : baseline.path("productRules")) {
                if ("妇幼人口30度膀胱镜加收8".equals(rule.path("name").asText())) {
                    thirtyRule = rule;
                    break;
                }
            }
            assertThat(thirtyRule).isNotNull();
            ObjectNode compiled = compile(thirtyRule);
            Map<String, Object> billRow = row(
                    "单包装包(老肯低温)",
                    "30°膀胱镜-1/Z7520",
                    "低温纸塑袋200*600",
                    1, 1, 36.0, 36.0);
            BillingConditionEvaluator.RowContext ctx = BillingConditionEvaluator.RowContext.fromRow(
                    billRow, 20, 1, null, null);
            assertThat(BillingConditionEvaluator.matchesRule(compiled, ctx)).isTrue();
        }
    }

    @Test
    void bladderMirror30DegreeChargesSingleExtraFee() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "单包装包(老肯低温)",
                "30°膀胱镜-1/Z7520",
                "低温纸塑袋200*600",
                1, 1, 36.0, 36.0));

        assertThat(result.pricingRule).contains("妇幼人口30度膀胱镜加收8");
        assertThat(result.pricingRule).doesNotContain("妇幼人口0度膀胱镜加收8");
        assertThat(result.notes.stream().filter(n -> n.contains("妇幼人口30度膀胱镜加收8")).count())
                .isEqualTo(1);
        assertThat(result.notes.stream().filter(n -> n.contains("妇幼人口0度膀胱镜加收8")).count())
                .isZero();
    }

    @Test
    void needleBoxSevenPlusOneStillFoldsWithExtraCount() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "额外包(纸塑袋)",
                "全冠套装(针7盒1)",
                "高温纸塑袋75*200",
                8, 1, 16.5, 16.5));

        assertThat(result.notes).anyMatch(n -> n.contains("妇幼人口针盒针5合1"));
        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
    }

    @Test
    void newFuqiangjingLensKeepsExtraFeeWithoutZeroOverride() throws Exception {
        PricingEngine engine = engineFromBaseline();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "单包装包(老肯低温)",
                "新腹腔镜镜头",
                "低温纸塑袋200*600",
                1, 1, 36.0, 36.0));

        assertThat(result.expectedUnitPrice).isEqualTo(36.0);
        assertThat(result.pricingRule).contains("加收8");
    }

    private static PricingEngine engineFromBaseline() throws Exception {
        try (InputStream in = HljFyRkExcelRulesRegressionTest.class.getResourceAsStream(
                "/billing-rules/baseline/HLJ-FY-RK.json")) {
            JsonNode baseline = MAPPER.readTree(in);
            ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
            rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
            ObjectNode special = (ObjectNode) rules.get("specialRules");
            ArrayNode folds = MAPPER.createArrayNode();
            ArrayNode extras = MAPPER.createArrayNode();
            for (JsonNode rule : baseline.path("productRules")) {
                ObjectNode compiled = compile(rule);
                if ("FOLD".equals(rule.path("ruleType").asText())) {
                    folds.add(compiled);
                } else if ("EXTRA_FEE".equals(rule.path("ruleType").asText())) {
                    extras.add(compiled);
                }
            }
            prepend(special, "foldRules", folds);
            prepend(special, "extraFees", extras);
            return new PricingEngine(rules);
        }
    }

    private static ObjectNode compile(JsonNode rule) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", rule.path("name").asText());
        if (rule.has("priority")) {
            node.put("priority", rule.path("priority").asInt());
        }
        node.putArray("hospitals").add(HOSPITAL);
        node.set("keywords", rule.path("keywords"));
        if (rule.has("fee")) {
            node.put("fee", rule.path("fee").asDouble());
        }
        if (rule.has("price")) {
            node.put("price", rule.path("price").asDouble());
        }
        if (rule.has("threshold")) {
            node.put("threshold", rule.path("threshold").asInt());
        }
        if (rule.has("foldRatio")) {
            node.put("foldRatio", rule.path("foldRatio").asDouble());
        }
        if (rule.has("minInstrumentCount")) {
            node.put("minInstrumentCount", rule.path("minInstrumentCount").asInt());
        }
        if (rule.has("maxInstrumentCount")) {
            node.put("maxInstrumentCount", rule.path("maxInstrumentCount").asInt());
        }
        if (rule.has("extraCount")) {
            node.put("extraCount", rule.path("extraCount").asInt());
        }
        if (rule.has("temperature")) {
            node.put("temperature", rule.path("temperature").asText());
        }
        node.put("skipPackaging", rule.path("skipPackaging").asBoolean(false));
        if (rule.has("keywordMatchMode")) {
            node.put("keywordMatchMode", rule.path("keywordMatchMode").asText());
        }
        if (rule.has("acceptedTypes")) {
            node.set("acceptedTypes", rule.path("acceptedTypes"));
        }
        return node;
    }

    private static void prepend(ObjectNode special, String field, ArrayNode customerRules) {
        ArrayNode merged = MAPPER.createArrayNode();
        merged.addAll(customerRules);
        merged.addAll(special.withArray(field));
        special.set(field, merged);
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
