package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LogisticsImport extends BaseEntity {

    private Long customerId;

    private Long jobId;

    /** YYYY-MM */
    private String billingMonth;

    private LocalDate tripDate;

    private String route;

    private Integer tripCount = 1;

    private Double feeAmount;

    private String notes;
}
