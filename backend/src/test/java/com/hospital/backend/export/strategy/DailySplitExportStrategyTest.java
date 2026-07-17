package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.service.impl.DailySplitServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySplitExportStrategyTest {

    @Mock
    private DailySplitServiceImpl dailySplitService;

    @InjectMocks
    private DailySplitExportStrategy strategy;

    @Test
    void exportsDailySplitWorkbook() throws Exception {
        when(dailySplitService.splitJobByDate(anyLong())).thenReturn(
                com.hospital.backend.common.Result.success(Map.of(
                        "dailyEntries", List.of(Map.of(
                                "deliveryDate", "2026-07-01",
                                "rowCount", 2,
                                "packCount", 3,
                                "originalTotal", 100.0,
                                "correctedTotal", 120.0)),
                        "dailyCorrectedSum", 120.0,
                        "monthlyCorrectedTotal", 120.0,
                        "reconciled", true)));

        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSourceDateRange("2026-07-01 ~ 2026-07-31");
        ExportContext context = ExportContext.builder()
                .jobId(1L)
                .exportType(ExportType.DAILY)
                .job(job)
                .rows(List.of(new HospitalReconciliationRow()))
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.DAILY_SPLIT)
                        .exportType(ExportType.DAILY)
                        .name("daily")
                        .build())
                .hospitalName("远东心脑血管")
                .build();

        var result = strategy.export(context);
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getFileName()).contains("daily");
    }
}
