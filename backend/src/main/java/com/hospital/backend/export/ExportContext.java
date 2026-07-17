package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExportContext {

    private final Long jobId;
    private final ExportType exportType;
    private final HospitalReconciliationJob job;
    private final List<HospitalReconciliationRow> rows;
    private final ResolvedExportTemplate template;
    private final Long customerId;
    private final String hospitalName;
}
