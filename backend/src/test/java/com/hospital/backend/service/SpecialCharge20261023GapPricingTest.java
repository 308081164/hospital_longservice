package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 2026-10-22 权威特殊收费表里已经能落到现有引擎的缺口：
 * 呼兰七折、省医院低温 180 封顶、市二院 ③–⑤⑧⑨⑩、骨伤科可建模条目。
 */
class SpecialCharge20261023GapPricingTest {

    private static final org.assertj.core.data.Offset<Double> CENT = offset(0.02);

    private ExportStageDiscountApplier exportDiscount;
    private ClerkRuleCompiler clerkCompiler;

    @BeforeEach
    void setUp() {
        exportDiscount = new ExportStageDiscountApplier();
        clerkCompiler = new ClerkRuleCompiler(new ClerkRuleIndex());
    }

    @Test
    void hulanStandardPriceIsSeventyPercent() throws Exception {
        ObjectNode compiled = clerkCompiler.compileForCustomer("HULAN-RM");
        BillRowItem row = pricedRow("器械包", "普通器械包", 100.0);
        var hit = exportDiscount.tryApplyDiscount(compiled, row, java.util.Set.of(), "HULAN-RM");
        assertThat(hit).isPresent();
        assertThat(hit.get().ruleName()).isEqualTo("标准价七折");
        assertThat(hit.get().unitPriceAfter()).isCloseTo(70.0, CENT);
    }

    @Test
    void shengHospitalLowTempTypesCapAt180AfterSixtyPercent() throws Exception {
        ObjectNode compiled = clerkCompiler.compileForCustomer("SHENG-YY-NG");
        BillRowItem lowTemp = pricedRow("器械包（低温等离子）", "低温器械包", 400.0);
        var capped = exportDiscount.tryApplyDiscount(compiled, lowTemp, java.util.Set.of(), "SHENG-YY-NG");
        assertThat(capped).isPresent();
        assertThat(capped.get().unitPriceAfter()).isCloseTo(180.0, CENT);

        BillRowItem ordinary = pricedRow("器械包（ZSD）", "手术包", 400.0);
        var uncapped = exportDiscount.tryApplyDiscount(compiled, ordinary, java.util.Set.of(), "SHENG-YY-NG");
        assertThat(uncapped).isPresent();
        assertThat(uncapped.get().unitPriceAfter()).isCloseTo(240.0, CENT);

        ObjectNode xiangfang = clerkCompiler.compileForCustomer("SHENG-YY-XF");
        BillRowItem xiangfangRow = pricedRow("额外包（ETO)", "ETO器械", 500.0);
        var xf = exportDiscount.tryApplyDiscount(xiangfang, xiangfangRow, java.util.Set.of(), "SHENG-YY-XF");
        assertThat(xf).isPresent();
        assertThat(xf.get().unitPriceAfter()).isCloseTo(180.0, CENT);
    }

    /**
     * 权威表「账单规则」第 96 行把老肯等温改成系统包类型「单包装包（老肯低温）」。
     * 全角括号与半角账单写法都要在六折后封顶 180；打字错误「等温」不封顶。
     */
    @Test
    void laokenLowTempTypeNameCapsAt180AfterSixtyPercent() {
        String source = "标准价格六折，低温器械（器械包（低温等离子）、器械包（ETO)、单包装包（老肯低温）、"
                + "单包装包（EO）、额外包（低温等离子）、额外包（ETO)）最高收费180元，超过180元，统一收费180元";
        List<String> types = List.of(
                "器械包（低温等离子）",
                "器械包（ETO)",
                "单包装包（老肯低温）",
                "单包装包（EO）",
                "额外包（低温等离子）",
                "额外包（ETO)");
        ClerkRuleIndex index = new ClerkRuleIndex();
        for (String code : List.of("SHENG-YY-NG", "SHENG-YY-XF")) {
            JsonNode rule = index.baselineForCustomer(code).path("rules").get(0);
            assertThat(rule.path("name").asText()).isEqualTo("标准价六折");
            assertThat(rule.path("sourceText").asText()).isEqualTo(source);
            assertThat(rule.path("sourceText").asText()).doesNotContain("等温");
            assertThat(rule.path("params").path("rate").asDouble()).isEqualTo(0.6);
            assertThat(rule.path("params").path("maxUnitPrice").asDouble()).isEqualTo(180.0);
            List<String> actualTypes = new ArrayList<>();
            rule.path("params").path("maxUnitPriceTypes").forEach(node -> actualTypes.add(node.asText()));
            assertThat(actualTypes).containsExactlyElementsOf(types);

            ObjectNode compiled = clerkCompiler.compileForCustomer(code);
            assertLaokenCap(compiled, code, "单包装包（老肯低温）", 180.0);
            assertLaokenCap(compiled, code, "单包装包(老肯低温)", 180.0);
            assertLaokenCap(compiled, code, "单包装包(老肯等温）", 240.0);
        }
    }

    private void assertLaokenCap(ObjectNode compiled, String code, String type, double expected) {
        BillRowItem row = pricedRow(type, "30度镜头-1", 400.0);
        var hit = exportDiscount.tryApplyDiscount(compiled, row, Set.of(), code);
        assertThat(hit).as(code + " " + type).isPresent();
        assertThat(hit.get().unitPriceAfter()).as(code + " " + type).isCloseTo(expected, CENT);

        BillRowItem applied = exportDiscount.apply(compiled, List.of(pricedRow(type, "30度镜头-1", 400.0))).get(0);
        assertThat(applied.getUnitPrice()).isCloseTo(expected, CENT);
        String note = String.join("\n", applied.getNotes());
        assertThat(note).contains("折前 400.00");
        assertThat(note).contains("标准价六折 400.00×0.6=240.00");
        if (expected <= 180.0) {
            assertThat(note).contains("封顶 180.00");
            assertThat(note).contains("240.00 超过封顶，单价取 180.00");
        } else {
            assertThat(note).doesNotContain("封顶");
        }
    }

    @Test
    void hrb2ndNamedPacksFollowOctoberExcel() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-2ND");

        assertThat(price(engine, "哈尔滨市第二医院", "额外包（仅消毒）", "止血带-2件", 2))
                .isCloseTo(8.0, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（无纺布）", "剖宫产-3/W120509060", 4))
                .isCloseTo(30.0, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "器械包（ZSD）", "手术室-缝合包", 2))
                .isCloseTo(19.0, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "器械包", "手术室-骨科包", 3))
                .isCloseTo(24.5, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "手机-1", 1))
                .isCloseTo(5.5, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "测试包/双", 4))
                .isCloseTo(22.0, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "测压器-12", 12))
                .isCloseTo(16.5, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "车针-6", 6))
                .isCloseTo(11.0, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "针线盒（3根针）", 1))
                .isCloseTo(5.5, CENT);
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "缝合针-3件", 3))
                .isCloseTo(5.5, CENT);
        assertThat(priced(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "内热针-12件", 12).pricingRule)
                .contains("通用小件");
        assertThat(price(engine, "哈尔滨市第二医院", "额外包（纸塑袋）", "内热针-12件", 12))
                .isCloseTo(16.5, CENT);
    }

    @Test
    void hrb2ndMatchedZeroDisinfectPriceDropsZeroUnitPriceAnomaly() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-2ND");

        PricingEngine.ProcessedResult matched = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院",
                "额外包(仅消毒)",
                "市二院外来333（只消毒）",
                "",
                333,
                1,
                0.0,
                0.0));
        assertThat(matched.expectedUnitPrice).isCloseTo(0.0, CENT);
        assertThat(matched.difference).isCloseTo(0.0, CENT);
        assertThat(matched.status).isEqualTo("unchanged");
        assertThat(matched.pricingRule).contains("市二院外来仅消毒0元");
        assertThat(joined(matched)).doesNotContain("字段核对错误");

        PricingEngine.ProcessedResult paper = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院",
                "额外包(仅消毒)",
                "止血带-12（只消毒不灭菌）",
                "高温纸塑袋150*260",
                12,
                1,
                8.0,
                8.0));
        assertThat(paper.expectedUnitPrice).isCloseTo(8.0, CENT);
        assertThat(paper.status).isEqualTo("unchanged");
        assertThat(paper.pricingRule).contains("市二院止血带固定8");
        assertThat(joined(paper)).doesNotContain("字段核对错误");

        PricingEngine.ProcessedResult unmatched = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院",
                "额外包(仅消毒)",
                "市二院止血带14个（只消毒）",
                "",
                14,
                1,
                0.0,
                0.0));
        assertThat(unmatched.status).isEqualTo("warning");
        assertThat(joined(unmatched)).contains("单价为 0");

        PricingEngine.ProcessedResult mismatch = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院",
                "额外包(仅消毒)",
                "止血带-2件",
                "",
                2,
                1,
                0.0,
                0.0));
        assertThat(mismatch.expectedUnitPrice).isCloseTo(8.0, CENT);
        assertThat(mismatch.status).isEqualTo("warning");
        assertThat(joined(mismatch)).contains("单价为 0");
    }

    @Test
    void hrb2ndSingleLowTempDoubleBagDoesNotCharge35() {
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());
        Map<String, Object> row = PricingEngineTestSupport.row(
                "哈尔滨市第二医院",
                "额外包(低温等离子)",
                "剪刀-1/双",
                "低温纸塑袋100*200",
                1,
                1,
                22.0,
                22.0);
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.expectedUnitPrice).isNotEqualTo(35.0);
        assertThat(joined(result)).doesNotContain("固定 35 元");
    }

    @Test
    void gushangExplicitPricesFollowOctoberExcel() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        String hospital = "哈尔滨市骨伤科医院";

        assertThat(price(engine, hospital, "额外包（纸塑袋）", "克氏针（0.8）-4件", 4))
                .isCloseTo(5.5, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "克氏针（1.5）-6件", 6))
                .isCloseTo(8.0, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "钻钥匙-1", 1))
                .isCloseTo(0.0, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "外架钢针-4件/双", 4))
                .isCloseTo(8.3, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "拉力螺钉-12件", 12))
                .isCloseTo(50.0, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "长钻头-2件/双", 2))
                .isCloseTo(5.8, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "钻头-2件/双/z7526", 2))
                .isCloseTo(5.8, CENT);
        assertThat(price(engine, hospital, "额外包（纸塑袋）", "普通器械/双", 4))
                .isCloseTo(22.3, CENT);

        Map<String, Object> kept = PricingEngineTestSupport.row(
                hospital, "额外包（无纺布）", "普通无纺布包", "无纺布", 2, 1, 18.8, 18.8);
        PricingEngine.ProcessedResult review = engine.processRow(kept);
        assertThat(review.expectedUnitPrice).isCloseTo(18.8, CENT);
        assertThat(review.pricingRule).contains("人工核对");
        assertThat(review.pricingPath).isEqualTo("preserve");

        PricingEngine.ProcessedResult three = priced(
                engine, hospital, "额外包（无纺布）", "普通无纺布包", 3);
        assertThat(three.pricingRule).doesNotContain("人工核对");
    }

    @Test
    void fuyiDressing120ClothIsPieceTimesFivePointFivePlusFivePointFiveThenGeneralDiscount() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("ZYY-D1");
        String hospital = "黑龙江中医药大学附属第一医院";

        PricingEngine.ProcessedResult four = priced(
                engine, hospital, "器械包（ZSD）", "换药包(120布)", 4);
        assertThat(four.expectedUnitPrice).isCloseTo(22.00, CENT);
        String notes = joined(four);
        assertThat(notes).contains("5.5");
        assertThat(notes).contains("附一对账八折");
        assertThat(notes).doesNotContain("6.25");
        assertThat(notes).doesNotContain("28.25");
        assertThat(four.pricingRule).doesNotContain("6.25");

        PricingEngine.ProcessedResult two = priced(
                engine, hospital, "器械包（ZSD）", "换药包(120布)", 2);
        assertThat(two.expectedUnitPrice).isCloseTo(13.19, CENT);
        assertThat(joined(two)).contains("附一九九九折");
    }

    @Test
    void octoberSheetGapsFollowWrittenFormulas() throws Exception {
        PricingEngine renkou = PricingEngineTestSupport.engineForCustomerCode("HLJ-FY-RK");
        String renkouName = "黑龙江省妇幼保健院（人口）";
        assertThat(priced(renkou, renkouName, "额外包低温等离子", "英姿低温组件-3件", 3).pricingRule)
                .contains("英姿低温组件");
        assertThat(price(renkou, renkouName, "额外包低温等离子", "英姿低温组件-8件", 8))
                .isEqualTo(price(renkou, renkouName, "额外包低温等离子", "密封胶圈-8件", 8));

        PricingEngine wuchang = PricingEngineTestSupport.engineForCustomerCode("WUCHANG-RM");
        assertThat(price(wuchang, "五常市人民医院", "额外包（纸塑袋）", "测试包/双", 4))
                .isCloseTo(22.0, CENT);
        assertThat(price(wuchang, "五常市人民医院", "额外包（纸塑袋）", "拉力螺钉-12件", 12))
                .isCloseTo(50.0, CENT);
        assertThat(priced(wuchang, "五常市人民医院", "额外包（纸塑袋）", "骨元针-6件", 6).pricingRule)
                .contains("骨元针");

        PricingEngine hsz = PricingEngineTestSupport.engineForCustomerCode("HRB-HSZ");
        PricingEngine.ProcessedResult needle = priced(
                hsz, "哈尔滨红十字医院", "额外包（纸塑袋）", "针类组件-12件", 12);
        assertThat(needle.pricingRule).contains("针类组件");
        assertThat(needle.expectedUnitPrice).isCloseTo(16.5, CENT);
        assertThat(priced(hsz, "哈尔滨红十字医院", "敷料包（无纺布）", "针盒-1/W6050", 1).expectedUnitPrice)
                .isCloseTo(16.5, CENT);
    }

    @Test
    void xiangfangDiscountStepWritesRateBeforeCapAndOmitsCapWhenNotApplied() throws Exception {
        ObjectNode compiled = clerkCompiler.compileForCustomer("SHENG-YY-XF");
        ReconciliationPricingOrchestrator orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(), exportDiscount);
        PricingEngine engine = echoingEngine();

        PricingEngine.ProcessedResult twentyFour = orchestrator.processRow(
                seedRow("器械包（低温等离子）", "外三-24件（盒1）/W12050", 388.0),
                engine, compiled, "SHENG-YY-XF", Set.of());
        assertThat(twentyFour.expectedUnitPrice).isCloseTo(180.0, CENT);
        assertThat(joined(twentyFour)).contains("标准价六折 388.00×0.6=232.80");
        assertThat(joined(twentyFour)).contains("封顶 180.00");
        assertThat(joined(twentyFour)).contains("232.80 超过封顶，单价取 180.00");
        assertThat(twentyFour.billingNotes.get("calculationSteps").toString())
                .contains("388.00×0.6=232.80")
                .contains("封顶 180.00");

        PricingEngine.ProcessedResult twentyTwo = orchestrator.processRow(
                seedRow("器械包（低温等离子）", "普镜-22件（盒1）/W12050", 344.0),
                engine, compiled, "SHENG-YY-XF", Set.of());
        assertThat(twentyTwo.expectedUnitPrice).isCloseTo(180.0, CENT);
        assertThat(joined(twentyTwo)).contains("344.00×0.6=206.40");
        assertThat(joined(twentyTwo)).contains("206.40 超过封顶，单价取 180.00");

        PricingEngine.ProcessedResult ureter = orchestrator.processRow(
                seedRow("器械包（低温等离子）", "输尿管镜-1/Z1570", 35.0),
                engine, compiled, "SHENG-YY-XF", Set.of());
        assertThat(ureter.expectedUnitPrice).isCloseTo(21.0, CENT);
        assertThat(joined(ureter)).contains("35.00×0.6=21.00");
        assertThat(joined(ureter)).doesNotContain("封顶");
        assertThat(joined(ureter)).doesNotContain("180");

        PricingEngine.ProcessedResult clothExtra = orchestrator.processRow(
                seedRow("额外包（棉布包装）", "棉布额外包", 400.0),
                engine, compiled, "SHENG-YY-XF", Set.of());
        assertThat(clothExtra.expectedUnitPrice).isCloseTo(240.0, CENT);
        assertThat(joined(clothExtra)).contains("400.00×0.6=240.00");
        assertThat(joined(clothExtra)).doesNotContain("封顶");
    }

    private static PricingEngine echoingEngine() {
        return new PricingEngine(JsonUtils.getObjectMapper().createObjectNode()) {
            @Override
            public ProcessedResult processRow(Map<String, Object> row) {
                ProcessedResult result = new ProcessedResult();
                result.expectedUnitPrice = ((Number) row.get("unitPrice")).doubleValue();
                result.pricingRule = "低温无纺布阶梯计费";
                result.pricingPath = "standard";
                result.notes = new ArrayList<>();
                return result;
            }
        };
    }

    private static Map<String, Object> seedRow(String type, String packName, double unitPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", "黑龙江省医院（香坊院区）");
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", "无纺布");
        row.put("instrumentCount", 1);
        row.put("packCount", 1);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", unitPrice);
        return row;
    }

    private static BillRowItem pricedRow(String type, String packName, double unitPrice) {
        BillRowItem row = new BillRowItem();
        row.setType(type);
        row.setPackName(packName);
        row.setPackageMaterial("无纺布");
        row.setPackCount(1);
        row.setInstrumentCount(1);
        row.setUnitPrice(unitPrice);
        row.setExpectedUnitPrice(unitPrice);
        row.setTotalPrice(unitPrice);
        return row;
    }

    private static double price(PricingEngine engine, String hospital, String type, String packName, int count) {
        return priced(engine, hospital, type, packName, count).expectedUnitPrice;
    }

    private static PricingEngine.ProcessedResult priced(
            PricingEngine engine, String hospital, String type, String packName, int count) {
        Map<String, Object> row = PricingEngineTestSupport.row(
                hospital, type, packName, "高温纸塑袋100*200", count, 1, 1.0, 1.0);
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.expectedUnitPrice).as(packName).isNotNull();
        return result;
    }

    private static String joined(PricingEngine.ProcessedResult result) {
        return result.notes == null ? "" : String.join("\n", result.notes);
    }
}
