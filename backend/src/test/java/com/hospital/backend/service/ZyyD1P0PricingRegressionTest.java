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
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("pricingMode", "hybrid");
        billingProfile.put("enabled", true);

        JsonNode stdSeed = MAPPER.readTree(getClass().getResourceAsStream(
                "/billing-seeds/phase-zyy-d1-standard-pricing-20260723.json"));
        JsonNode override = stdSeed.path("customerUpdates").get(0).path("standardPricingOverride");
        deepMerge(rules, (ObjectNode) override);

        ObjectNode specialRules = (ObjectNode) rules.get("specialRules");
        ArrayNode fixedPrices = (ArrayNode) specialRules.get("fixedPrices");
        ArrayNode foldRules = (ArrayNode) specialRules.get("foldRules");

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

        appendIncrementalFixedPriceRules(fixedPrices,
                "/billing-seeds/phase-zyy-d1-waier-huanbao-20260730.json");
        appendIncrementalFixedPriceRules(fixedPrices,
                "/billing-seeds/phase-zyy-d1-gongqiangjing-jingtou-20260730.json");
        engine = new PricingEngine(rules);
    }

    private void appendIncrementalFixedPriceRules(ArrayNode fixedPrices, String classpath) throws Exception {
        JsonNode seed = MAPPER.readTree(getClass().getResourceAsStream(classpath));
        for (JsonNode ruleNode : seed.path("newRules")) {
            ObjectNode compiled = (ObjectNode) ruleNode.deepCopy();
            var departments = BillingConditionEvaluator.parseDepartmentList(
                    ruleNode.path("conditionsJson").asText(null));
            if (!departments.isEmpty()) {
                compiled.set("departments", MAPPER.valueToTree(departments));
            }
            if (ruleNode.hasNonNull("billingMode")) {
                compiled.put("billingMode", ruleNode.path("billingMode").asText());
            }
            fixedPrices.insert(0, compiled);
        }
    }

    @Test
    void shouldPriceCottonBallJarAtFuyi25cmBagRate() {
        assertWarning(row("棉球缸-1/z2530", "敷料包(纸塑袋)", "高温纸塑袋250*300", 1, 1, 12.8, 12.8), 12.79);
    }

    @Test
    void shouldFlagExpectedPriceCorrections() {
        assertWarning(row("换药包(120布)", "器械包(ZSD)", "", 3, 3, 22.6, 67.8), 21.99);
        assertWarning(row("30°腹腔镜-1/z2060", "器械包(ZSD)", "", 1, 1, 28, 28), 30.4);
        assertWarning(row("辅料包", "敷料包(无纺布包)", "无纺布-150×150-50g", 0, 2, 0, 0), 28);
        assertWarning(row("球内注药-3件/Z1526", "额外包(纸塑袋)", "高温纸塑袋150*260", 24, 8, 17.6, 140.8), 13.2);
        assertWarning(row("保温杯-1Z2044", "额外包(ETO)", "高温纸塑袋200*440", 2, 2, 10.4, 20.8), 17.6);
        assertWarning(row("膀胱取石钳-1/z1560", "额外包(ETO)", "高温纸塑袋150*600", 1, 1, 8.8, 8.8), 20);
        assertWarning(row("冲洗头-120/z2030", "额外包(低温等离子)", "低温纸塑袋200*300", 120, 1, 328, 328), 310.4);
        assertWarning(row("王树人特器-26（筐1）/w12050", "额外包(ETO)", "无纺布-120×120-50g", 27, 1, 292.8, 292.8), 328);
    }

    @Test
    void shouldNotFlagStandardNonwovenRows() {
        assertUnchanged(row("持物钳罐-2/w6050", "额外包(无纺布)", "无纺布-60×60-50g", 2, 1, 13.2, 13.2));
        assertUnchanged(row("洗手服（XL号）/W15050", "敷料包(无纺布包)", "无纺布-150×150-50g", 0, 1, 28, 28));
        assertUnchanged(row("腹腔镜包", "器械包", "", 1, 1, 154, 154));
        assertUnchanged(row("王树人特器-1件/z2060", "额外包(ETO)", "高温纸塑袋200*440", 1, 1, 22.38, 22.38));
        assertUnchanged(row("保温杯(高温)-1Z2044", "额外包(纸塑袋)", "高温纸塑袋200*440", 1, 1, 10.39, 10.39));
    }

    @Test
    void shouldPriceStandardHighTempPaperPlasticWithFuyiTable() {
        assertWarning(row("示例包", "额外包(纸塑袋)", "高温纸塑袋150*260", 8, 1, 10.4, 83.2), 35.2);
        assertUnchanged(row("单件包", "额外包(纸塑袋)", "高温纸塑袋150*260", 1, 1, 8.79, 8.79));
    }

    @Test
    void shouldPriceLowTempSterilizationMaterialByBagSize() {
        assertUnchanged(row("持针器", "额外包(ETO)", "低温灭菌 20cm", 1, 1, 22.38, 22.38));
    }

    @Test
    void shouldFoldGanlanAndChongxiHeadFivePiecesIntoOne() {
        PricingEngine.ProcessedResult ganlan20 = engine.processRow(row(
                "橄榄头-20/Z2030", "额外包(低温等离子)", "低温纸塑袋200*300", 20, 1, 70.4, 1408));
        assertThat(ganlan20.notes).anyMatch(n -> n.contains("橄榄头5件算1件") && n.contains("折算为 4 件"));

        PricingEngine.ProcessedResult chongxi5 = engine.processRow(row(
                "冲洗头-50/z2030", "额外包(低温等离子)", "低温纸塑袋200*300", 5, 1, 240, 240));
        assertThat(chongxi5.notes).anyMatch(n -> n.contains("冲洗头5件算1件") && n.contains("折算为 1 件"));

        PricingEngine.ProcessedResult chongxi120Fixed = engine.processRow(row(
                "冲洗头-120/z2030", "额外包(低温等离子)", "低温纸塑袋200*300", 120, 1, 328, 328));
        assertThat(chongxi120Fixed.notes).noneMatch(n -> n.contains("冲洗头5件算1件"));
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

    private void assertWarning(Map<String, Object> row, double expectedUnit) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isCloseTo(expectedUnit, offset(0.02));
    }

    private void assertUnchanged(Map<String, Object> row) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void shouldPriceWaierHuanYaoBaoAt2199PerPack() {
        assertWarning(row("外二", "换药包(120布)", "器械包(ZSD)", "", 3, 3, 22.6, 67.8), 21.99);
    }

    @Test
    void shouldPriceGongqiangjingJingtou3At5274PerPack() {
        Map<String, Object> row = row(
                "宫腔镜", "镜头-3件(盒1)/Z2060", "额外包(低温等离子)", "低温纸塑袋200*300",
                3, 2, 52.8, 105.6);
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isCloseTo(52.74, offset(0.02));
        assertThat(result.correctedTotalPrice).isCloseTo(105.48, offset(0.02));
    }

    private Map<String, Object> row(String department, String packName, String type, String material,
                                    int instrumentCount, int packCount,
                                    double unitPrice, double totalPrice) {
        Map<String, Object> row = row(packName, type, material, instrumentCount, packCount, unitPrice, totalPrice);
        row.put("sheetName", department);
        row.put("department", department);
        return row;
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
