package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportTemplateResolverKeys;
import com.hospital.backend.export.strategy.InstrumentAuditExportStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentAuditExportStrategyTest {

    @Mock
    private com.hospital.backend.mapper.ExternalInstrumentMapper externalInstrumentMapper;

    private InstrumentAuditExportStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new InstrumentAuditExportStrategy(
                new InstrumentAuditDataBuilder(),
                new SummarySheetWriter(),
                externalInstrumentMapper);
    }

    @Test
    void exportsNonEmptyWorkbookWithAggregatedRows() throws Exception {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("高温");
        row.setPackName("常规包");
        row.setCategoryNo("A001");
        row.setPackCount(2);
        row.setInstrumentCount(5);
        row.setCorrectedTotalPrice(100.0);

        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setId(1L);
        job.setHospitalName("测试医院");

        ExportContext context = ExportContext.builder()
                .jobId(1L)
                .exportType(ExportType.INSTRUMENT_AUDIT)
                .job(job)
                .rows(List.of(row))
                .hospitalName("测试医院")
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.INSTRUMENT_AUDIT)
                        .sheetConfigJson("{\"strategyKey\":\"instrument_audit\"}")
                        .build())
                .build();

        ExportResult result = strategy.export(context);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().length).isGreaterThan(512);
        assertThat(result.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.INSTRUMENT_AUDIT);
    }

    @Test
    void externalInstrumentLayoutUsesExternalMapper() throws Exception {
        when(externalInstrumentMapper.selectByJobId(2L)).thenReturn(List.of());

        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setId(2L);
        job.setHospitalName("市五院");

        ExportContext context = ExportContext.builder()
                .jobId(2L)
                .exportType(ExportType.INSTRUMENT_AUDIT)
                .job(job)
                .rows(List.of())
                .hospitalName("市五院")
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.INSTRUMENT_AUDIT)
                        .sheetConfigJson(
                                "{\"strategyKey\":\"instrument_audit\",\"layout\":\"external_instrument\"}")
                        .build())
                .build();

        ExportResult result = strategy.export(context);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getFileName()).contains("instrument_audit");
    }
}
