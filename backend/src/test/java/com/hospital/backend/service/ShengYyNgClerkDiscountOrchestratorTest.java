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
 * 省医院南岗（SHENG-YY-NG）内勤「标准价六折」在对账编排器中的落库口径。
 */
class ShengYyNgClerkDiscountOrchestratorTest {

    private static final String HOSPITAL = "黑龙江省医院（南岗院区）";

    private ReconciliationPricingOrchestrator orchestrator;
    private PricingEngine pricingEngine;
    private ObjectNode clerkCompiled;

    @BeforeEach
    void setUp() throws Exception {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        pricingEngine = PricingEngineTestSupport.engineForCustomerCode("SHENG-YY-NG");
        ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());
        clerkCompiled = compiler.compileForCustomer("SHENG-YY-NG");
        assertThat(clerkCompiled).isNotNull();
    }

    @Test
    void standardTierRowGetsSixtyPercentClerkDiscountOnExpectedUnitPrice() {
        Map<String, Object> row = baseRow(6.6, 6.6);
        PricingEngine.ProcessedResult engineOnly = pricingEngine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isNotNull();
        double preDiscount = engineOnly.expectedUnitPrice;
        double expectedDiscounted = Math.round(preDiscount * 0.6 * 100.0) / 100.0;

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "SHENG-YY-NG", Set.of());

        assertThat(result.expectedUnitPrice).isCloseTo(expectedDiscounted, offset(0.02));
        assertThat(result.billingNotes.get("clerkDiscountRuleName")).isEqualTo("标准价六折");
        assertThat(((Number) result.billingNotes.get("priceBeforeDiscount")).doubleValue())
                .isCloseTo(preDiscount, offset(0.02));
        assertThat(result.billingNotes.get("priceAfterDiscount")).isEqualTo(result.expectedUnitPrice);
        assertThat(result.correctedTotalPrice).isCloseTo(expectedDiscounted, offset(0.02));
        double billUnit = ((Number) row.get("unitPrice")).doubleValue();
        if (Math.abs(billUnit - expectedDiscounted) <= 0.02) {
            assertThat(result.status).isEqualTo("unchanged");
        }
    }

    @Test
    void surgeryPackSpecialPriceThenSixtyPercent() {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("packName", "手术包-6件（双信封包装！）");
        row.put("type", "器械包（ZSD）");
        row.put("packageMaterial", "双信封");
        row.put("instrumentCount", 6);
        row.put("packCount", 1);
        row.put("unitPrice", 21.83);
        row.put("totalPrice", 21.83);

        PricingEngine.ProcessedResult engineOnly = pricingEngine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(36.38, offset(0.02));
        assertThat(engineOnly.pricingRule).contains("手术包6件");

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "SHENG-YY-NG", Set.of());
        assertThat(result.expectedUnitPrice).isCloseTo(21.83, offset(0.02));
        assertThat(result.billingNotes.get("clerkDiscountRuleName")).isEqualTo("标准价六折");
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价六折"));
    }

    @Test
    void surgeryPackTotalRoundsAfterMultiplyingTheUnroundedUnit() {
        assertSurgeryPackTotal(3, 18, 65.48);
        assertSurgeryPackTotal(4, 24, 87.31);
    }

    private void assertSurgeryPackTotal(int packCount, int instrumentCount, double billTotal) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("packName", "手术包-6件（双信封包装！）");
        row.put("type", "器械包（ZSD）");
        row.put("packageMaterial", "双信封");
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", 21.83);
        row.put("totalPrice", billTotal);

        PricingEngine.ProcessedResult engineOnly = pricingEngine.processRow(row);
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(36.38, offset(0.001));

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "SHENG-YY-NG", Set.of());
        assertThat(result.expectedUnitPrice).isCloseTo(21.83, offset(0.001));
        assertThat(result.unroundedUnitPrice).isNotNull();
        assertThat(result.correctedTotalPrice).isCloseTo(billTotal, offset(0.001));
        assertThat(result.difference).isCloseTo(0.0, offset(0.001));
        assertThat(((Number) result.billingNotes.get("unroundedUnitPrice")).doubleValue())
                .isCloseTo(result.unroundedUnitPrice, offset(0.0001));
    }

    @Test
    void doubleHeadCuretteDoesNotUseDoubleBagRuleButStillGetsSixtyPercent() {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("packName", "双头挖勺-1/Z7526");
        row.put("type", "额外包（纸塑袋）");
        row.put("packageMaterial", "高温纸塑袋100*200");
        row.put("instrumentCount", 1);
        row.put("packCount", 1);
        row.put("unitPrice", 4.80);
        row.put("totalPrice", 4.80);

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "SHENG-YY-NG", Set.of());
        assertThat(result.pricingRule).doesNotContain("双<3");
        assertThat(result.expectedUnitPrice).isCloseTo(4.80, offset(0.02));
        assertThat(result.notes).anyMatch(note -> note.contains("导出阶段折扣：标准价六折"));
    }

    @Test
    void clerkIndexResolvesCustomerCodeWhenNameMatchesBaseline() {
        ClerkRuleIndex index = new ClerkRuleIndex();
        assertThat(index.resolveCustomerCodeByHospitalName(HOSPITAL)).isEqualTo("SHENG-YY-NG");
        assertThat(index.resolveCustomerCodeByHospitalName("省医院南岗")).isEqualTo("SHENG-YY-NG");
        assertThat(index.resolveCustomerCodeByHospitalName("省医院香坊")).isEqualTo("SHENG-YY-XF");
        assertThat(index.resolveCustomerCodeByHospitalName("黑龙江省医院（香坊院区）")).isEqualTo("SHENG-YY-XF");
        assertThat(index.resolveCustomerCodeByHospitalName("黑龙江省第二医院（南岗区）")).isEqualTo("ERYY-NG");
        assertThat(index.resolveCustomerCodeByHospitalName("省二院南岗")).isEqualTo("ERYY-NG");
        assertThat(index.resolveCustomerCodeByHospitalName("黑龙江省第二医院（松北区）")).isEqualTo("ERYY-SB");
        assertThat(index.resolveCustomerCodeByHospitalName("省二院松北")).isEqualTo("ERYY-SB");
    }

    private static Map<String, Object> baseRow(double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("packName", "开口器-1/Z1526");
        row.put("type", "额外包（纸塑袋）");
        row.put("packageMaterial", "高温纸塑袋100*200");
        row.put("instrumentCount", 1);
        row.put("packCount", 1);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }
}
