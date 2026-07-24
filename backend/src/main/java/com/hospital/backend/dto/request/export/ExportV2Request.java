package com.hospital.backend.dto.request.export;

import lombok.Data;

@Data
public class ExportV2Request {

    /** bill | settlement | dept_summary | price_summary | instrument_audit | logistics_allocation | grand_summary */
    private String exportType = "bill";

    /** Optional explicit export_template.id */
    private Long templateId;

    /**
     * Reserved for future strategy-only exports. Bill/settlement always use the legacy POI template path.
     */
    private Boolean useStrategyEngine = true;
}
