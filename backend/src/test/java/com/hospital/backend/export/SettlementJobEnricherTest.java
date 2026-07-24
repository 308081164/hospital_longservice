package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.SettlementJobFieldsApplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementJobEnricherTest {

    @Mock
    private SettlementJobFieldsApplier settlementJobFieldsApplier;

    @InjectMocks
    private SettlementJobEnricher enricher;

    private final SettlementTemplateFiller filler = new SettlementTemplateFiller();

    @Test
    void enrichForExportDelegatesToFieldsApplier() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("{}");

        enricher.enrichForExport(job, compiledRules, List.of());

        verify(settlementJobFieldsApplier).applyAll(eq(job), same(compiledRules), eq(List.of()), eq(false));
    }

    @Test
    void daowaiNoLogisticsRowWithoutPolicy() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("道外区人民医院");
        job.setLogisticsFee(0.0);
        job.setLogisticsTripCount(0);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"LOGISTICS","name":"结款不收物流","params":{"feePerTrip":0}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows =
                filler.buildFeeRows(job, 821.0, compiledRules);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用")
                .doesNotContain("物流费用");
    }

    @Test
    void taipingNoLogisticsRowWhenFeePerTripZero() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("太平人民医院");
        job.setLogisticsFee(0.0);
        job.setLogisticsTripCount(0);
        job.setLogisticsBreakdown("{\"feePerTrip\":0,\"payableFee\":0,\"tripCount\":0}");

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"LOGISTICS","name":"不收物流费","params":{"feePerTrip":0}}
                ]}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows =
                filler.buildFeeRows(job, 5431.02, compiledRules);

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("灭菌费用")
                .doesNotContain("物流费用");
    }

    @Test
    void jiuzhouShowsZeroLogisticsRowWhenPayableZero() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江九洲妇科医院");
        job.setLogisticsFee(0.0);
        job.setLogisticsTripCount(5);
        job.setLogisticsBreakdown("""
                {"feePerTrip":50,"payableFee":0,"tripCount":5,"waivedTrips":3}
                """);

        HospitalReconciliationRow htRow = new HospitalReconciliationRow();
        htRow.setPackName("高温包");
        htRow.setType("HT");
        htRow.setCorrectedTotalPrice(2000.0);
        HospitalReconciliationRow ltRow = new HospitalReconciliationRow();
        ltRow.setPackName("低温包");
        ltRow.setType("LT");
        ltRow.setCorrectedTotalPrice(1000.0);

        JsonNode compiledRules = JsonUtils.getObjectMapper().readTree("""
                {"billingPolicies":[
                  {"policyType":"DISCOUNT","name":"高温5折","scope":{"temperature":"HT"},"params":{"rate":0.5}},
                  {"policyType":"DISCOUNT","name":"低温7折","scope":{"temperature":"LT"},"params":{"rate":0.7}},
                  {"policyType":"LOGISTICS","name":"6月免3次物流","params":{"feePerTrip":50,"waivedTrips":3}}
                ],"settlement":{"omitZeroRows":true}}
                """);

        List<SettlementTemplateFiller.SettlementFeeRow> rows =
                filler.buildFeeRows(job, 3000.0, compiledRules, List.of(htRow, ltRow));

        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .contains("高温灭菌费用", "低温灭菌费用", "物流费用");
        assertThat(rows.stream()
                .filter(r -> "物流费用".equals(r.getItemName()))
                .findFirst()
                .orElseThrow()
                .getAmount()).isEqualTo(0.0);
        assertThat(rows).extracting(SettlementTemplateFiller.SettlementFeeRow::getItemName)
                .doesNotContain("敷料");
    }
}
