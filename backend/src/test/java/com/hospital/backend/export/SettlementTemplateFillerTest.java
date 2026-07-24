package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementTemplateFillerTest {

    private final SettlementTemplateFiller filler = new SettlementTemplateFiller();

    @Test
    void addsMinChargeAdjustmentRowFromMonthlyBreakdown() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setMonthlyBreakdown("{\"adjustment\":1500,\"minCharge\":8000,\"rawSterilizeTotal\":6500}");
        job.setLogisticsFee(80.5);
        job.setLogisticsTripCount(1);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"LOGISTICS","name":"物流80.5","params":{"feePerTrip":80.5}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 6500, compiledRules);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用", "物流费用", "低消补差");
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
                        "灭菌费用",
                        "加急灭菌费",
                        "加急灭菌费(减免后)",
                        "加急物流费",
                        "加急物流费(减免后)",
                        "设备抵扣");
        assertThat(filler.computeTotalAmount(rows)).isEqualTo(10000 + 37.5 + 135 - 3270);
    }

    @Test
    void splitsSterilizeByTemperatureForHtLtPolicies() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江维多利亚妇产医院");
        job.setLogisticsFee(850.0);
        job.setLogisticsTripCount(17);

        HospitalReconciliationRow htRow = new HospitalReconciliationRow();
        htRow.setPackName("高温包");
        htRow.setType("HT");
        htRow.setCorrectedTotalPrice(7660.25);
        HospitalReconciliationRow ltRow = new HospitalReconciliationRow();
        ltRow.setPackName("低温包");
        ltRow.setType("LT");
        ltRow.setCorrectedTotalPrice(1193.5);

        String rulesJson = """
                {"billingPolicies":[
                  {"policyType":"DISCOUNT","name":"高温5折","scope":{"temperature":"HT"},"params":{"rate":0.5}},
                  {"policyType":"DISCOUNT","name":"低温7折","scope":{"temperature":"LT"},"params":{"rate":0.7}},
                  {"policyType":"LOGISTICS","name":"物流50","params":{"feePerTrip":50}}
                ]}
                """;
        JsonNode compiledRules = com.hospital.backend.common.JsonUtils.getObjectMapper()
                .readTree(rulesJson);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(
                job, 8853.75, compiledRules, List.of(htRow, ltRow));

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("高温灭菌费用", "低温灭菌费用", "敷料", "物流费用");
    }

    @Test
    void embedsMinChargeInTotalWithoutAdjustmentRow() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("悦美芳华医疗门诊医院");
        job.setSettlementAdjustment(68.0);
        job.setMonthlyBreakdown("{\"adjustment\":68,\"minCharge\":1000}");
        job.setLogisticsFee(100.0);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"MONTHLY_SETTLEMENT","name":"低消1000",
                   "params":{"minCharge":1000,"settlementOmitMinChargeRow":true}},
                  {"policyType":"LOGISTICS","name":"物流100","params":{"monthlyFlatFee":100}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 832.0, compiledRules);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用", "物流费用")
                .doesNotContain("低消补差");
        assertThat(filler.computeTotalAmount(rows, job)).isEqualTo(1000.0);
    }

    @Test
    void addsHulanTcmSpecialPackRows() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("呼兰中医院");
        job.setLogisticsFee(2405.0);

        HospitalReconciliationRow surgical = new HospitalReconciliationRow();
        surgical.setPackName("外科包");
        surgical.setPackCount(1);
        surgical.setUnitPrice(249.5);
        surgical.setCorrectedTotalPrice(249.5);
        HospitalReconciliationRow appendicitis = new HospitalReconciliationRow();
        appendicitis.setPackName("阑尾包");
        appendicitis.setPackCount(4);
        appendicitis.setUnitPrice(288.0);
        appendicitis.setCorrectedTotalPrice(1152.0);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(
                job, 5451.5, null, List.of(surgical, appendicitis));

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用", "外科包", "阑尾包");
    }
}
