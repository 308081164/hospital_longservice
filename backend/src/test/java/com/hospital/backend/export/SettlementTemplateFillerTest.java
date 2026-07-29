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

    @Test
    void addsSettlementExtraRowForFuyiBillingMonth() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江中医药大学附属第一医院");
        job.setSourceFileName("6月__附一6月账单__附一6月结款函.xlsx");
        job.setLogisticsFee(100.0);
        job.setLogisticsTripCount(2);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"SETTLEMENT_EXTRA","name":"手术一区洗涤费",
                   "params":{"itemName":"手术一区洗涤费用","amountByMonth":{"2026-06":3987.1}}},
                  {"policyType":"LOGISTICS","name":"物流","params":{"feePerTrip":50}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 150000, compiledRules);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("手术一区洗涤费用", "灭菌费用", "物流费用");
        assertThat(rows.stream()
                .filter(r -> "手术一区洗涤费用".equals(r.getItemName()))
                .mapToDouble(SettlementTemplateFiller.SettlementFeeRow::getAmount)
                .findFirst()
                .orElse(0)).isEqualTo(3987.1);
    }

    @Test
    void buildsXinfaSettlementRowsWithHtDiscountAndDressing() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("新发红十字医院");
        job.setLogisticsFee(1050.0);
        job.setLogisticsTripCount(21);
        job.setUrgentBreakdown("""
                {"urgentRowCount":3,"baseMultiplier":1.25,"adjustedMultiplier":1.025,
                "nominalSurcharge":3861.88,"adjustedSurcharge":3788.1,"urgentTripCount":4,
                "urgentLogisticsFeePerTrip":150,"urgentLogisticsDiscountRate":0.9,
                "nominalUrgentLogisticsTotal":600,"adjustedUrgentLogisticsTotal":540}
                """);

        HospitalReconciliationRow htRow = new HospitalReconciliationRow();
        htRow.setType("器械包（纸塑袋）");
        htRow.setPackName("腹腔镜包");
        htRow.setCorrectedTotalPrice(13536.5);
        HospitalReconciliationRow dressingRow = new HospitalReconciliationRow();
        dressingRow.setType("敷料包(无纺布包)");
        dressingRow.setPackName("敷料");
        dressingRow.setCorrectedTotalPrice(5378.5);

        JsonNode compiledRules = com.hospital.backend.common.JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"DISCOUNT","name":"结款函高温75折","scope":{"temperature":"HT"},
                   "params":{"rate":0.75,"applyStage":"settlement_only"}},
                  {"policyType":"LOGISTICS","name":"结款物流50","params":{"feePerTrip":50}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(
                job, 21866.0, compiledRules, List.of(htRow, dressingRow));

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains(
                        "系统灭菌费用",
                        "高温75折后费用（实收）",
                        "敷料",
                        "物流费用",
                        "加急灭菌费",
                        "减免后加急物流费");
    }

    @Test
    void buildsXinfaSettlementFromOverrideAndPresetUrgent() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("新发红十字医院");
        job.setSourceFileName("6月__新发红十字医院6结款函.xlsx");
        job.setUrgentBreakdown("""
                {"baseMultiplier":1.25,"adjustedMultiplier":1.025,
                "nominalUrgentTotal":3861.88,"adjustedUrgentTotal":3788.1,
                "urgentTripCount":4,"urgentLogisticsFeePerTrip":150,
                "urgentLogisticsDiscountRate":0.9,
                "nominalUrgentLogisticsTotal":600,"adjustedUrgentLogisticsTotal":540}
                """);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"URGENT","name":"新发加急","params":{"urgentLineMode":"total"}},
                  {"policyType":"SETTLEMENT_OVERRIDE","name":"新发对齐",
                   "params":{"xinfaSystemSterilizeAmountByMonth":{"2026-06":13536.5},
                             "xinfaHtDiscountedAmountByMonth":{"2026-06":10152.375},
                             "logisticsAmountByMonth":{"2026-06":1050},
                             "minChargeAdjustmentByMonth":{"2026-06":0}}}
                ]}
                """);

        HospitalReconciliationRow dressingRow = new HospitalReconciliationRow();
        dressingRow.setType("敷料包(无纺布包)");
        dressingRow.setPackName("敷料");
        dressingRow.setCorrectedTotalPrice(5378.5);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(
                job, 1000, compiledRules, List.of(dressingRow));

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains(
                        "系统灭菌费用",
                        "高温75折后费用（实收）",
                        "敷料",
                        "物流费用",
                        "加急灭菌费",
                        "加急灭菌费(减免后)",
                        "加急物流费",
                        "减免后加急物流费",
                        "低消补差");
        assertThat(rows.stream().filter(r -> "系统灭菌费用".equals(r.getItemName()))
                .mapToDouble(SettlementTemplateFiller.SettlementFeeRow::getAmount).findFirst().orElse(0))
                .isEqualTo(13536.5);
    }

    @Test
    void appliesSterilizeOverrideForZyyD2Ng() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江中医药大学附属第二医院（南岗）");
        job.setSourceFileName("6月__中医附二6月结款涵.xlsx");

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"SETTLEMENT_OVERRIDE","name":"附二南岗结款灭菌对齐",
                   "params":{"sterilizeAmountByMonth":{"2026-06":39865.0}}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 40885.0, compiledRules);

        assertThat(rows.stream().filter(r -> "灭菌费用".equals(r.getItemName()))
                .mapToDouble(SettlementTemplateFiller.SettlementFeeRow::getAmount).findFirst().orElse(0))
                .isEqualTo(39865.0);
        assertThat(filler.computeTotalAmount(rows)).isEqualTo(39865.0);
    }

    @Test
    void overrideSterilizeSkipsSettlementDiscount() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("太平人民医院");
        job.setSourceFileName("6月__太平人民2026.5.13-2026.6.15结款函.xlsx");

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"DISCOUNT","name":"结款7.5折",
                   "params":{"rate":0.75,"applyStage":"settlement_only"},"priority":5},
                  {"policyType":"SETTLEMENT_OVERRIDE","name":"太平结款灭菌对齐",
                   "params":{"sterilizeAmountByMonth":{"2026-06":5431.03}}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 8000.0, compiledRules);

        assertThat(rows.stream().filter(r -> "灭菌费用".equals(r.getItemName()))
                .mapToDouble(SettlementTemplateFiller.SettlementFeeRow::getAmount).findFirst().orElse(0))
                .isEqualTo(5431.03);
    }

    @Test
    void appliesWave4OverrideForShengYyXf() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江省医院（香坊院区）");
        job.setSourceFileName("6月__香坊省医院5.21-6.20结款函.xlsx");

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"SETTLEMENT_OVERRIDE","name":"省医院香坊结款对齐",
                   "params":{
                     "sterilizeAmountByMonth":{"2026-06":178443.0},
                     "logisticsAmountByMonth":{"2026-06":3650.0}
                   }}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows = filler.buildFeeRows(job, 200000.0, compiledRules);

        assertThat(filler.computeTotalAmount(rows)).isEqualTo(182093.0);
    }
}
