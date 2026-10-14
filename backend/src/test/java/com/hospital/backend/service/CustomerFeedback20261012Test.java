package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 2026-10-12 特殊收费表与客户反馈：全院共性问题的独立验收。
 */
class CustomerFeedback20261012Test {

    private ReconciliationPricingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
    }

    @Test
    void widerBagIsOuterRegardlessOfWriteOrder() {
        DoubleBagWidths.Pair pair = DoubleBagWidths.order("75*200", "100*260");
        assertThat(pair.outerWidthMm()).isEqualTo(100);
        assertThat(pair.innerWidthMm()).isEqualTo(75);
    }

    @Test
    void listedBagsKeepOuterWidthWhenNarrowerBagIsWrittenFirst() throws Exception {
        PricingEngine engine = engineWithBagRule("all", false, 2);
        PricingEngine.ProcessedResult result = engine.processRow(paperRow(
                "黑龙江省第二医院（松北区）", "剪刀-2/双", "高温纸塑袋75*200,高温纸塑袋100*260", 2, 30));
        assertThat(String.join("\n", result.notes)).contains("外层 10cm").contains("内层 7.5cm");
    }

    @Test
    void singleListedBagIsNotCopiedIntoASecondBag() throws Exception {
        PricingEngine engine = engineWithBagRule("all", false, 2);
        PricingEngine.ProcessedResult result = engine.processRow(paperRow(
                "黑龙江省第二医院（松北区）", "剪刀-1/双", "高温纸塑袋100*260", 1, 30));
        assertThat(result.expectedUnitPrice).isCloseTo(8.0, within(0.001));
        assertThat(String.join("\n", result.notes)).contains("不复制第二只");
    }

    @Test
    void songbeiDoesNotCapAndAddsOnlyOuterBagAtThreePieces() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("ERYY-SB");
        PricingEngine.ProcessedResult under = engine.processRow(paperRow(
                "黑龙江省第二医院（松北区）",
                "剪刀-2/双",
                "高温纸塑袋200*300,高温纸塑袋250*350",
                2,
                40));
        assertThat(under.expectedUnitPrice).isCloseTo(29.0, within(0.001));
        assertThat(under.pricingRule).contains("省二松北双<3");

        PricingEngine.ProcessedResult over = engine.processRow(paperRow(
                "黑龙江省第二医院（松北区）",
                "剪刀-3/双",
                "高温纸塑袋75*200,高温纸塑袋200*300",
                3,
                40));
        assertThat(over.expectedUnitPrice).isCloseTo(24.0, within(0.001));
        assertThat(over.pricingRule).contains("仅外层");
    }

    @Test
    void shengHospitalKeepsOwnSixteenPointFiveCap() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("SHENG-YY-NG");
        PricingEngine.ProcessedResult under = engine.processRow(paperRow(
                "黑龙江省医院（南岗院区）",
                "剪刀-2/双",
                "高温纸塑袋200*300,高温纸塑袋250*350",
                2,
                40));
        assertThat(under.expectedUnitPrice).isCloseTo(16.5, within(0.001));

        PricingEngine.ProcessedResult over = engine.processRow(paperRow(
                "黑龙江省医院（南岗院区）",
                "剪刀-3/双",
                "高温纸塑袋200*300,高温纸塑袋250*350",
                3,
                40));
        assertThat(over.expectedUnitPrice).isCloseTo(16.5, within(0.001));
        assertThat(over.pricingRule).contains("不加袋");
    }

    @Test
    void lowTempSingleDoubleIsThirtyFiveExceptCitySecondHospital() {
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());
        PricingEngine.ProcessedResult province = engine.processRow(lowTempRow("黑龙江省第二医院"));
        PricingEngine.ProcessedResult city = engine.processRow(lowTempRow("哈尔滨市第二医院"));
        assertThat(province.expectedUnitPrice).isCloseTo(35.0, within(0.001));
        assertThat(city.expectedUnitPrice).isNotCloseTo(35.0, within(0.001));
    }

    @Test
    void clerkPriceStillCarriesFieldCheck() {
        ObjectNode clerk = clerkPrice("止血钳-2", 8.0);
        Map<String, Object> row = bill("止血钳-2", "额外包(纸塑袋)", "高温纸塑袋75*200", 9, 1, 8.0);
        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, new PricingEngine(PricingEngineTestSupport.defaultRules()), clerk, "AOLAN-YY", Set.of());
        assertThat(result.expectedUnitPrice).isCloseTo(8.0, within(0.001));
        assertThat(result.pricingRule).contains("内勤计价");
        assertThat(result.notes).anyMatch(note -> note.startsWith("【字段核对】") && !note.startsWith("【字段核对】规则单价"));
    }

    @Test
    void specialPriceStillGetsClerkDiscountAndClerkFixedPriceDoesNot() {
        ObjectNode clerk = JsonUtils.getObjectMapper().createObjectNode();
        clerk.putArray("clerkRules");
        clerk.putArray("billingPolicies").add(discount("五折", 0.5));
        PricingEngine skipEngine = stubEngine(10.0, true);
        Map<String, Object> row = bill("普通包", "额外包(纸塑袋)", "高温纸塑袋75*200", 1, 1, 10.0);
        PricingEngine.ProcessedResult special = orchestrator.processRow(row, skipEngine, clerk, "AOLAN-YY", Set.of());
        assertThat(special.expectedUnitPrice).isCloseTo(5.0, within(0.001));
        assertThat(special.notes).anyMatch(note -> note.contains("导出阶段折扣：五折，单价 5.00"));

        ObjectNode clerkFixed = clerkPrice("普通包", 8.0);
        clerkFixed.putArray("billingPolicies").add(discount("五折", 0.5));
        PricingEngine.ProcessedResult fixed = orchestrator.processRow(row, skipEngine, clerkFixed, "AOLAN-YY", Set.of());
        assertThat(fixed.expectedUnitPrice).isCloseTo(8.0, within(0.001));
        assertThat(fixed.pricingRule).contains("内勤计价");
        assertThat(fixed.notes).noneMatch(note -> note.contains("导出阶段折扣"));
    }

    @Test
    void dressingPackKeepsOriginalPriceAtTwoHospitals() {
        ObjectNode clerk = JsonUtils.getObjectMapper().createObjectNode();
        clerk.putArray("billingPolicies").add(discount("七折", 0.7));
        for (String hospital : List.of("黑龙江省第二医院（松北区）", "黑龙江省医院（香坊院区）")) {
            Map<String, Object> row = bill("纱布", "敷料包(无纺布)", "无纺布-90×90-50g", 1, 1, 12.0);
            row.put("hospitalName", hospital);
            PricingEngine.ProcessedResult result = orchestrator.processRow(
                    row, new PricingEngine(PricingEngineTestSupport.defaultRules()), clerk, "ERYY-SB", Set.of());
            assertThat(result.expectedUnitPrice).as(hospital).isCloseTo(12.0, within(0.001));
            assertThat(result.notes).anyMatch(note -> note.contains("不做包材核对"));
            assertThat(result.notes).noneMatch(note -> note.contains("导出阶段折扣"));
        }
    }

    @Test
    void finalPriceNoteUsesDiscountedUnitPrice() {
        ObjectNode clerk = JsonUtils.getObjectMapper().createObjectNode();
        clerk.putArray("billingPolicies").add(discount("七折", 0.7));
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules()) {
            @Override
            public ProcessedResult processRow(Map<String, Object> row) {
                ProcessedResult result = new ProcessedResult();
                result.expectedUnitPrice = 30.0;
                result.pricingRule = "测试规则";
                result.pricingPath = "fixed";
                result.notes = new ArrayList<>();
                result.notes.add("【字段核对】规则单价 30.00 元与原账单价 30.00 元不一致，请人工复核。");
                return result;
            }
        };
        Map<String, Object> row = bill("普通包", "额外包(纸塑袋)", "高温纸塑袋75*200", 1, 1, 30.0);
        PricingEngine.ProcessedResult result = orchestrator.processRow(row, engine, clerk, "AOLAN-YY", Set.of());
        assertThat(result.expectedUnitPrice).isCloseTo(21.0, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("规则单价 21.00"));
        assertThat(result.notes).noneMatch(note -> note.contains("规则单价 30"));
    }

    @Test
    void manualReviewStopsAtBillPrice() throws Exception {
        ObjectNode rules = PricingEngineTestSupport.defaultRules();
        ObjectNode rule = rules.with("specialRules").withArray("fixedPrices").addObject();
        rule.put("name", "人工核对测试");
        rule.put("price", 5.5);
        rule.put("pricePerInstrument", true);
        rule.put("manualReview", true);
        rule.putArray("keywords").add("核对包");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(paperRow(
                "测试医院", "核对包-1", "高温纸塑袋100*260", 1, 20));
        assertThat(result.expectedUnitPrice).isCloseTo(20.0, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("人工核对") || note.contains("原价"));
        assertThat(result.notes).noneMatch(note -> note.contains("纸塑袋费"));
    }

    @Test
    void genericSheetRulesDoNotRequireHospitalName() {
        Map<String, Object> rules = DefaultPricingTemplate.buildRulesMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = (Map<String, Object>) rules.get("needle");
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) needle.get("keywords");
        assertThat(keywords).contains("克氏针", "卷棉子");
        @SuppressWarnings("unchecked")
        Map<String, Object> special = (Map<String, Object>) rules.get("specialRules");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixed = (List<Map<String, Object>>) special.get("fixedPrices");
        assertThat(fixed).anyMatch(rule -> "软镜".equals(String.valueOf(((List<?>) rule.get("keywords")).get(0)))
                && Double.valueOf(300).equals(toDouble(rule.get("price"))));
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());
        assertThat(engine.processRow(lowTempRow("任意医院")).expectedUnitPrice).isCloseTo(35.0, within(0.001));
    }

    private static PricingEngine engineWithBagRule(String bagAddon, boolean cap, int maxCount) {
        ObjectNode rules = PricingEngineTestSupport.defaultRules();
        ObjectNode rule = rules.with("specialRules").withArray("fixedPrices").addObject();
        rule.put("name", "双层袋测试");
        rule.put("price", 5.5);
        rule.put("pricePerInstrument", true);
        rule.put("maxInstrumentCount", maxCount);
        rule.putArray("keywords").add("/双@contains").add("/(双)@contains").add("/（双）@contains");
        ArrayNode conditions = rule.putArray("conditions");
        conditions.addObject().put("field", "bagAddon").put("value", bagAddon);
        if (cap) {
            conditions.addObject().put("field", "capDoubleBag").put("value", true);
        }
        return new PricingEngine(rules);
    }

    private static PricingEngine stubEngine(double price, boolean skipDiscount) {
        return new PricingEngine(JsonUtils.getObjectMapper().createObjectNode()) {
            @Override
            public ProcessedResult processRow(Map<String, Object> row) {
                ProcessedResult result = new ProcessedResult();
                result.expectedUnitPrice = price;
                result.pricingRule = "测试规则";
                result.pricingPath = "fixed";
                result.skipHospitalDiscount = skipDiscount;
                result.notes = new ArrayList<>();
                return result;
            }
        };
    }

    private static Map<String, Object> paperRow(
            String hospital, String packName, String material, int count, double unitPrice) {
        return bill(packName, "额外包（纸塑袋）", material, count, 1, unitPrice, hospital);
    }

    private static Map<String, Object> lowTempRow(String hospital) {
        return bill("剪刀-1/双", "额外包(低温等离子)", "低温纸塑袋100*200", 1, 1, 22.0, hospital);
    }

    private static Map<String, Object> bill(
            String packName, String type, String material, int count, int packs, double unitPrice) {
        return bill(packName, type, material, count, packs, unitPrice, "测试医院");
    }

    private static Map<String, Object> bill(
            String packName, String type, String material, int count, int packs, double unitPrice, String hospital) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hospitalName", hospital);
        row.put("department", "手术室");
        row.put("packName", packName);
        row.put("type", type);
        row.put("packageMaterial", material);
        row.put("instrumentCount", count);
        row.put("packCount", packs);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", unitPrice * packs);
        return row;
    }

    private static ObjectNode clerkPrice(String packName, double price) {
        ObjectNode compiled = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode clerkRules = compiled.putArray("clerkRules");
        ObjectNode rule = clerkRules.addObject();
        rule.put("ruleType", "PACK_NAME_PRICE");
        rule.put("stage", "bill_export");
        rule.put("name", packName);
        rule.put("isActive", true);
        rule.put("priority", 10);
        ObjectNode params = rule.putObject("params");
        params.putArray("packNameKeywords").add(packName);
        params.put("unitPrice", price);
        params.put("unitPriceMode", "FIXED");
        return compiled;
    }

    private static ObjectNode discount(String name, double rate) {
        ObjectNode policy = JsonUtils.getObjectMapper().createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", name);
        policy.put("priority", 10);
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("rate", rate);
        return policy;
    }

    private static Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
