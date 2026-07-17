package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExternalInstrument extends BaseEntity {

    private Long customerId;

    /** NULL = catalog price row; non-null = billing period line linked to job */
    private Long reconciliationJobId;

    private String categoryNo;

    private String packName;

    private String department;

    private String packageMaterial;

    private String patientName;

    private LocalDate usageDate;

    private Integer packCount = 1;

    private Integer instrumentCount = 0;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String notes;

    private Boolean isActive = true;
}
