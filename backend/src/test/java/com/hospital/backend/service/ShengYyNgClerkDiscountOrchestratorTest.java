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
    void clerkIndexResolvesCustomerCodeWhenNameMatchesBaseline() {
        ClerkRuleIndex index = new ClerkRuleIndex();
        assertThat(index.resolveCustomerCodeByHospitalName(HOSPITAL)).isEqualTo("SHENG-YY-NG");
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
