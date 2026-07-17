package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementTemplateFillerTest {

    private final SettlementTemplateFiller filler = new SettlementTemplateFiller();

    @Test
    void addsMinChargeAdjustmentRowFromMonthlyBreakdown() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setMonthlyBreakdown("{\"adjustment\":1500,\"minCharge\":8000,\"rawSterilizeTotal\":6500}");
        job.setLogisticsFee(80.5);
        job.setLogisticsTripCount(1);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 6500);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费", "物流费", "低消补差");
        assertThat(filler.computeTotalAmount(rows)).isEqualTo(6500 + 80.5 + 1500);
    }

    @Test
    void usesSettlementAdjustmentWhenBreakdownMissing() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSettlementAdjustment(-500.0);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 9000);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("封顶调减");
    }

    @Test
    void addsUrgentAndDeductionRows() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setUrgentBreakdown("""
                {"urgentRowCount":2,"baseMultiplier":1.25,"adjustedMultiplier":1.025,
                "nominalSurcharge":375,"adjustedSurcharge":37.5,"urgentTripCount":1,
                "urgentLogisticsFeePerTrip":150,"urgentLogisticsDiscountRate":0.9,
                "nominalUrgentLogisticsTotal":150,"adjustedUrgentLogisticsTotal":135}
                """);
        job.setDeductionBreakdown("""
                {"monthlyAmount":3270,"deductionAmount":-3270,"policyName":"设备抵扣"}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 10000);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains(
                        "灭菌费",
                        "加急灭菌费",
                        "加急灭菌费(减免后)",
                        "加急物流费",
                        "加急物流费(减免后)",
                        "设备抵扣");
        assertThat(filler.computeTotalAmount(rows)).isEqualTo(10000 + 37.5 + 135 - 3270);
    }
}
