package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 附一 6 月 P0 规则回归：校验应校价行与不应误报的标准无纺布行。
 */
class ZyyD1P0PricingRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PricingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("pricingMode", "special_only");
        ArrayNode fixedPrices = rules.withObject("specialRules").withArray("fixedPrices");
        ArrayNode foldRules = rules.withObject("specialRules").withArray("foldRules");

        JsonNode seed = MAPPER.readTree(getClass().getResourceAsStream("/billing-seeds/phase-zyy-d1-fuyi.json"));
        JsonNode productRules = seed.path("profiles").get(0).path("productRules");
        for (JsonNode node : productRules) {
            ObjectNode compiled = (ObjectNode) node.deepCopy();
            String ruleType = node.path("ruleType").asText("FIXED_PRICE");
            if ("FOLD".equals(ruleType)) {
                foldRules.add(compiled);
                continue;
            }
            if ("PRICE_PER_INSTRUMENT".equals(ruleType)) {
                compiled.put("pricePerInstrument", true);
            }
            fixedPrices.insert(0, compiled);
        }
        engine = new PricingEngine(rules);
    }

    @Test
    void shouldFlagExpectedPriceCorrections() {
        assertWarning(row("换药包(120布)", "器械包(ZSD)", "", 3, 3, 22.6, 67.8), 21.99);
        assertWarning(row("30°腹腔镜-1/z2060", "器械包(ZSD)", "", 1, 1, 28, 28), 30.38);
        assertWarning(row("辅料包", "敷料包(无纺布包)", "无纺布-150×150-50g", 0, 2, 0, 0), 28);
        assertWarning(row("球内注药-3件/Z1526", "额外包(纸塑袋)", "高温纸塑袋150*260", 24, 8, 17.6, 140.8), 13.2);
        assertWarning(row("保温杯-1Z2044", "额外包(ETO)", "高温纸塑袋200*440", 2, 2, 10.4, 20.8), 17.58);
        assertWarning(row("膀胱取石钳-1/z1560", "额外包(ETO)", "高温纸塑袋150*600", 1, 1, 8.8, 8.8), 19.98);
        assertWarning(row("冲洗头-120/z2030", "额外包(低温等离子)", "低温纸塑袋200*300", 120, 1, 328, 328), 310.4);
        assertWarning(row("王树人特器-26（筐1）/w12050", "额外包(ETO)", "无纺布-120×120-50g", 27, 1, 292.8, 292.8), 328);
    }

    @Test
    void shouldNotFlagStandardNonwovenRows() {
        assertUnchanged(row("持物钳罐-2/w6050", "额外包(无纺布)", "无纺布-60×60-50g", 2, 1, 13.2, 13.2));
        assertUnchanged(row("洗手服（XL号）/W15050", "敷料包(无纺布包)", "无纺布-150×150-50g", 0, 1, 28, 28));
        assertUnchanged(row("腹腔镜包", "器械包", "", 1, 1, 154, 154));
        assertUnchanged(row("王树人特器-1件/z2060", "额外包(ETO)", "高温纸塑袋200*440", 1, 1, 22.4, 22.4));
        assertUnchanged(row("保温杯(高温)-1Z2044", "额外包(纸塑袋)", "高温纸塑袋200*440", 1, 1, 10.4, 10.4));
    }

    private void assertWarning(Map<String, Object> row, double expectedUnit) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isCloseTo(expectedUnit, offset(0.02));
    }

    private void assertUnchanged(Map<String, Object> row) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("unchanged");
    }

    private Map<String, Object> row(String packName, String type, String material,
                                    int instrumentCount, int packCount,
                                    double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "黑龙江中医药大学附属第一医院");
        row.put("packName", packName);
        row.put("type", type);
        row.put("packageMaterial", material);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }
}
