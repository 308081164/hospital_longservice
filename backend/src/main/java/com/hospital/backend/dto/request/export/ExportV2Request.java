package com.hospital.backend.dto.request.export;

import lombok.Data;

@Data
public class ExportV2Request {

    /** bill | settlement | dept_summary */
    private String exportType = "bill";

    /** Optional explicit export_template.id */
    private Long templateId;

    /** When true, skip legacy bridge and use v2 strategy engine only */
    private Boolean useStrategyEngine = true;
}
