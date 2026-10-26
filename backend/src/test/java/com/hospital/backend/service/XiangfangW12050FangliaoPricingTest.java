package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 香坊权威表项目③：器械包-多少件/W12050，额外包（无纺布），件数*5.5＋5元，只有放疗科。
 * 六折由内勤导出阶段乘，不靠关掉 skipDiscount。
 */
class XiangfangW12050FangliaoPricingTest {

    private static final String HOSPITAL = "黑龙江省医院（香坊院区）";
    private static final String RULE = "省医院香坊放疗科W12050按件5.5加5";

    private ReconciliationPricingOrchestrator orchestrator;
    private PricingEngine pricingEngine;
    private ObjectNode clerkCompiled;

    @BeforeEach
    void setUp() throws Exception {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        pricingEngine = PricingEngineTestSupport.engineForCustomerCode("SHENG-YY-XF");
        clerkCompiled = new ClerkRuleCompiler(new ClerkRuleIndex()).compileForCustomer("SHENG-YY-XF");
        assertThat(clerkCompiled).isNotNull();
    }

    @Test
    void fangliaoFifteenPiecesAddsFiveThenSixtyPercent() {
        PricingEngine.ProcessedResult result = price("放疗科", "器械包-15件/W12050", 15, 21.00);
        assertThat(result.pricingRule).contains(RULE);
        assertThat(result.pricingRule).doesNotContain("高温无纺布");
        assertThat(result.billingNotes.get("priceBeforeDiscount")).isEqualTo(87.5);
        assertThat(result.expectedUnitPrice).isCloseTo(52.50, offset(0.001));
        assertThat(result.billingNotes.get("clerkDiscountRuleName")).isEqualTo("标准价六折");
    }

    @Test
    void fangliaoFourPiecesAddsFiveThenSixtyPercent() {
        PricingEngine.ProcessedResult result = price("放疗科", "器械包-4件/W12050", 4, 16.20);
        assertThat(result.pricingRule).contains(RULE);
        assertThat(result.billingNotes.get("priceBeforeDiscount")).isEqualTo(27.0);
        assertThat(result.expectedUnitPrice).isCloseTo(16.20, offset(0.001));
    }

    @Test
    void otherDepartmentKeepsStandardNonwovenTier() {
        PricingEngine.ProcessedResult fifteen = price("手术室", "器械包-15件/W12050", 15, 21.00);
        assertThat(fifteen.pricingRule).contains("高温无纺布计费");
        assertThat(fifteen.pricingRule).doesNotContain(RULE);
        assertThat(fifteen.billingNotes.get("priceBeforeDiscount")).isEqualTo(82.5);
        assertThat(fifteen.expectedUnitPrice).isCloseTo(49.50, offset(0.001));

        PricingEngine.ProcessedResult four = price("妇科病房", "器械包-4件/W12050", 4, 16.20);
        assertThat(four.pricingRule).contains("高温无纺布计费");
        assertThat(four.billingNotes.get("priceBeforeDiscount")).isEqualTo(22.0);
        assertThat(four.expectedUnitPrice).isCloseTo(13.20, offset(0.001));
    }

    @Test
    void halfwidthPackTypeStillMatchesWhenSheetIsFangliao() {
        Map<String, Object> row = row("放疗科", "额外包(无纺布)", "器械包-15件/W12050", 15, 21.00);
        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "SHENG-YY-XF", Set.of());
        assertThat(result.expectedUnitPrice).isCloseTo(52.50, offset(0.001));
    }

    private PricingEngine.ProcessedResult price(String sheetName, String packName, int instruments, double billPrice) {
        Map<String, Object> row = row(sheetName, "额外包(无纺布)", packName, instruments, billPrice);
        return orchestrator.processRow(row, pricingEngine, clerkCompiled, "SHENG-YY-XF", Set.of());
    }

    /** 生产行只有 sheetName，没有 department 字段。 */
    private static Map<String, Object> row(
            String sheetName, String type, String packName, int instruments, double billPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("sheetName", sheetName);
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", "无纺布-120x120-50g");
        row.put("instrumentCount", instruments);
        row.put("packCount", 1);
        row.put("unitPrice", billPrice);
        row.put("totalPrice", billPrice);
        return row;
    }
}
