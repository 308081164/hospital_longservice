package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportTemplate extends BaseEntity {

    private Long customerId;

    /** bill | settlement | dept_summary | price_summary | instrument_audit | daily | logistics_allocation | grand_summary */
    private String templateType;

    private String name;

    private String storagePath;

    /** JSON: removeColumns, insertColumns, renameColumns, keepColumns */
    private String columnMapping;

    /** JSON: strategyKey, customerCode, extra options */
    private String sheetConfig;

    private Boolean isActive = true;
}
