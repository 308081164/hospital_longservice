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
 * 松北特色公式价算完后仍走内勤「标准价七折」。一口价「钉≥10 固定 50」保持不打折。
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
    void boneCuretteSpecialFormulaThenSeventyPercentMatchesBill() {
        Map<String, Object> row = row(
                "骨刮匙（双头）-1/Z7530",
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
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，单价 5.60"));
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
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价七折，单价 53.55"));
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
    void nailFixedPriceStaysFiftyWithoutClerkDiscount() {
        Map<String, Object> row = row(
                "接骨钉-12",
                "额外包（纸塑袋）",
                "高温纸塑袋100*200",
                12,
                1,
                50.0,
                50.0);
        PricingEngine.ProcessedResult result = price(row);
        assertThat(result.pricingRule).contains("钉≥10固定50");
        assertThat(result.expectedUnitPrice).isCloseTo(50.0, within(0.001));
        assertThat(result.notes).noneMatch(note -> note.contains("导出阶段折扣"));
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
