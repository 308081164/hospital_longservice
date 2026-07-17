package com.hospital.backend.dto.response.external;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExternalInstrumentResponse {

    private Long id;

    private Long customerId;

    private Long reconciliationJobId;

    private String categoryNo;

    private String packName;

    private String department;

    private String packageMaterial;

    private String patientName;

    private LocalDate usageDate;

    private Integer packCount;

    private Integer instrumentCount;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String notes;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
