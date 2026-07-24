package com.hospital.backend.export;

import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportTemplateResolverKeys;
import com.hospital.backend.export.strategy.StandardPriceSummaryExportStrategy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StandardPriceSummaryExportStrategyTest {

    private final StandardPriceSummaryExportStrategy strategy =
            new StandardPriceSummaryExportStrategy(new SummarySheetWriter());

    @Test
    void exportsPriceSummaryFromAllocation() throws Exception {
        AllocationResult allocation = new AllocationResult();
        Map<String, Double> categories = new LinkedHashMap<>();
        categories.put("高温", 500.0);
        categories.put("低温", 200.0);
        allocation.setPriceSummaryByCategory(categories);
        allocation.setBalanced(true);

        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setAllocationResult("""
                {"priceSummaryByCategory":{"高温":500.0,"低温":200.0},"balanced":true}
                """);

        ExportContext context = ExportContext.builder()
                .jobId(10L)
                .exportType(ExportType.PRICE_SUMMARY)
                .job(job)
                .rows(List.of())
                .hospitalName("祖研南岗")
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY)
                        .sheetConfigJson("{\"sheetName\":\"汇总\"}")
                        .build())
                .build();

        ExportResult result = strategy.export(context);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().length).isGreaterThan(256);
        assertThat(result.getFileName()).contains("price_summary");
    }

    @Test
    void fallsBackToRowRollupWhenAllocationMissing() throws Exception {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("高温");
        row.setCorrectedTotalPrice(88.0);

        HospitalReconciliationJob job = new HospitalReconciliationJob();

        ExportContext context = ExportContext.builder()
                .jobId(11L)
                .exportType(ExportType.PRICE_SUMMARY)
                .job(job)
                .rows(List.of(row))
                .hospitalName("测试")
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.STANDARD_PRICE_SUMMARY)
                        .build())
                .build();

        ExportResult result = strategy.export(context);

        assertThat(result.getContent()).isNotEmpty();
    }
}
