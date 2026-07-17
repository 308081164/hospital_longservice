package com.hospital.backend.dto.request.export;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveExportTemplateRequest {

    private Long customerId;

    @NotBlank
    private String templateType;

    @NotBlank
    private String name;

    private String storagePath;

    /** JSON string */
    private String columnMapping;

    /** JSON string — must include strategyKey for v2 routing */
    private String sheetConfig;

    private Boolean isActive = true;
}
