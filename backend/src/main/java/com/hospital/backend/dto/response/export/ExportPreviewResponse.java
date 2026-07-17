package com.hospital.backend.dto.response.export;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExportPreviewResponse {

    private Long jobId;
    private String exportType;
    private Long templateId;
    private String templateName;
    private String strategyKey;
    private boolean customerOverride;
    private int rowCount;
    private String hospitalName;
}
