package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 松北特色公式价算完后仍走内勤「标准价七折」。内勤一口价不再打折；特色一口价同样乘七折。
 */
class SongbeiSpecialDiscountOrchestratorTest {

    private static final String HOSPITAL = "黑龙江省第二医院（松北区）";

    private ReconciliationPricingOrchestrator orchestrator;
    private PricingEngine engine;
    private ObjectNode clerk;

    @BeforeEach
    void setUp() throws Exception {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        engine = PricingEngineTestSupport.engineForCustomerCode("ERYY-SB");
        clerk = new ClerkRuleCompiler(new ClerkRuleIndex()).compileForCustomer("ERYY-SB");
        assertThat(clerk).isNotNull();
    }

    @Test
    void slashDoubleBagSpecialFormulaThenSeventyPercentMatchesBill() {
        Map<String, Object> row = row(
                "骨刮匙-1/双/Z7530",
                "额外包（纸塑袋）",
                "高温纸塑袋75*100",
                4,
                4,
                5.60,
                22.40);
        PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(8.00, within(0.001));
        assertThat(engineOnly.pricingRule).contains("省二松北双<3");

        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.expectedUnitPrice).isCloseTo(5.60, within(0.001));
        assertThat(result.correctedTotalPrice).isCloseTo(22.40, within(0.001));
        assertThat(result.difference).isCloseTo(0.0, within(0.001));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.billingNotes.get("clerkDiscountRuleName")).isEqualTo("标准价七折");
        assertThat(((Number) result.billingNotes.get("priceBeforeDiscount")).doubleValue())
                .isCloseTo(8.00, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，折前 8.00，标准价七折 8.00×0.7=5.60"));
        assertThat(result.notes).noneMatch(note -> note.contains("封顶"));
    }

    @Test
    void smallCavityPackSpecialFormulaThenSeventyPercentMatchesBill() {
        Map<String, Object> row = row(
                "小腔包（手术室）",
                "器械包",
                "高温塑袋",
                13,
                1,
                53.55,
                53.55);
        PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(76.50, within(0.001));

        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.expectedUnitPrice).isCloseTo(53.55, within(0.001));
        assertThat(result.pricingRule).contains("小腔包");
        assertThat(result.difference).isCloseTo(0.0, within(0.001));
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，折前 76.50，标准价七折 76.50×0.7=53.55"));
    }

    @Test
    void threePieceDoubleUsesOuterBagThenSeventyPercent() {
        Map<String, Object> row = row(
                "剪刀-3/双",
                "额外包（纸塑袋）",
                "高温纸塑袋75*200,高温纸塑袋200*300",
                3,
                1,
                16.80,
                16.80);
        PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(24.00, within(0.001));

        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.expectedUnitPrice).isCloseTo(16.80, within(0.001));
        assertThat(result.pricingRule).contains("仅外层");
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void standardRowStillGetsSeventyPercent() {
        Map<String, Object> row = row(
                "软式-1/Z1026",
                "额外包（纸塑袋）",
                "高温纸塑袋100*200",
                1,
                1,
                5.60,
                5.60);
        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.pricingRule).doesNotContain("省二松北");
        assertThat(result.expectedUnitPrice).isCloseTo(5.60, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void doubleHeadNameDoesNotMatchDoubleBagRule() {
        Map<String, Object> row = row(
                "骨刮匙（双头）-1/Z7530",
                "额外包（纸塑袋）",
                "高温纸塑袋75*100",
                4,
                4,
                5.60,
                22.40);
        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.pricingRule).doesNotContain("双<3");
        assertThat(result.expectedUnitPrice).isCloseTo(5.60, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折"));
    }

    @Test
    void kirschnerNeedleFoldsBeforeDoubleBagThenSeventyPercent() {
        Map<String, Object> row = row(
                "克氏针1.2-1/双Z7530",
                "额外包（纸塑袋）",
                "高温纸塑袋75*200,高温纸塑袋100*350",
                2,
                1,
                7.35,
                7.35);
        PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(10.50, within(0.001));
        assertThat(engineOnly.pricingRule).contains("省二松北双<3");
        assertThat(engineOnly.pricingRule).contains("通用小件5合1");
        assertThat(engineOnly.notes).anyMatch(note -> note.contains("折算为 1 件"));

        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.expectedUnitPrice).isCloseTo(7.35, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，折前 10.50，标准价七折 10.50×0.7=7.35"));
        assertThat(((Number) result.billingNotes.get("priceBeforeDiscount")).doubleValue())
                .isCloseTo(10.50, within(0.001));
    }

    @Test
    void parenthesizedSlashDoubleStillMatches() {
        for (String packName : java.util.List.of("剪刀-1/(双)", "剪刀-1/（双）")) {
            Map<String, Object> row = row(
                    packName,
                    "额外包（纸塑袋）",
                    "高温纸塑袋100*200",
                    1,
                    1,
                    5.60,
                    5.60);
            PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
            assertThat(engineOnly.pricingRule).as(packName).contains("省二松北双<3");
        }
    }

    @Test
    void nailFixedPriceThenSeventyPercent() {
        Map<String, Object> row = row(
                "接骨钉-12",
                "额外包（纸塑袋）",
                "高温纸塑袋100*200",
                12,
                1,
                35.0,
                35.0);
        PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(50.0, within(0.001));
        assertThat(engineOnly.pricingRule).contains("钉≥10固定50");

        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.pricingRule).contains("钉≥10固定50");
        assertThat(result.expectedUnitPrice).isCloseTo(35.0, within(0.001));
        assertThat(((Number) result.billingNotes.get("priceBeforeDiscount")).doubleValue())
                .isCloseTo(50.0, within(0.001));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，折前 50.00，标准价七折 50.00×0.7=35.00"));
        assertThat(result.status).isEqualTo("unchanged");
    }

    private PricingEngine.ProcessedResult price(Map<String, Object> row) {
        return orchestrator.processRow(row, engine, clerk, "ERYY-SB", Set.of());
    }

    private static Map<String, Object> row(
            String packName,
            String type,
            String material,
            int instrumentCount,
            int packCount,
            double unitPrice,
            double totalPrice) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("department", "手术室");
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
