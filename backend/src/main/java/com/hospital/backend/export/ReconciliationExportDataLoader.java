package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.export.strategy.ExportTemplateResolverKeys;
import com.hospital.backend.service.CustomerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReconciliationExportDataLoader {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final CustomerResolver customerResolver;
    private final ExportTemplateResolver templateResolver;
    private final ExportNameMappingApplier exportNameMappingApplier;
    private final GuoyaoQuantityAlgorithm guoyaoQuantityAlgorithm;

    public ExportContext loadContext(Long jobId, ExportType exportType, Long templateIdOverride) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Reconciliation job not found: " + jobId);
        }
        List<HospitalReconciliationRow> rows =
                rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        Long customerId = customerResolver.resolveByName(job.getHospitalName())
                .map(c -> c.getId())
                .orElse(null);
        rows = exportNameMappingApplier.apply(customerId, rows);
        ResolvedExportTemplate template = templateResolver.resolve(customerId, exportType, templateIdOverride);
        if (ExportTemplateResolverKeys.GUOYAO_BILL.equals(template.getStrategyKey())) {
            rows.forEach(guoyaoQuantityAlgorithm::applyToRow);
        }
        return ExportContext.builder()
                .jobId(jobId)
                .exportType(exportType)
                .job(job)
                .rows(rows)
                .template(template)
                .customerId(customerId)
                .hospitalName(job.getHospitalName())
                .build();
    }

    public Optional<HospitalReconciliationJob> findJob(Long jobId) {
        return Optional.ofNullable(jobMapper.selectById(jobId));
    }
}
