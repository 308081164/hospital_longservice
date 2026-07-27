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
    private Boolean billingEnabled;
    private String billLayout;
    private String d8DisplaySource;
    private String exportProfileLabel;
    private String expectedSheetMode;
    /** Distinct sheet names in job rows (for layout mismatch warning) */
    private Integer distinctSheetCount;
}
