package com.hospital.backend.export.model;

import com.hospital.backend.export.ExportType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResolvedExportTemplate {

    private final Long templateId;
    private final Long customerId;
    private final ExportType exportType;
    private final String name;
    private final String storagePath;
    private final String strategyKey;
    private final ColumnMappingConfig columnMapping;
    private final String sheetConfigJson;
    private final boolean customerOverride;
}
