package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
