package com.hospital.backend.dto.response.export;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExportTemplateResponse {

    private Long id;
    private Long customerId;
    private String templateType;
    private String name;
    private String storagePath;
    private String columnMapping;
    private String sheetConfig;
    private Boolean isActive;
    private String strategyKey;
}
