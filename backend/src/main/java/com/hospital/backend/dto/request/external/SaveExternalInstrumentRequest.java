package com.hospital.backend.dto.request.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveExternalInstrumentRequest {

    @NotBlank
    private String categoryNo;

    @NotBlank
    private String packName;

    private String department;

    private String packageMaterial;

    private String patientName;

    private LocalDate usageDate;

    private Integer packCount = 1;

    private Integer instrumentCount = 0;

    @NotNull
    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String notes;

    private Boolean isActive = true;

    /** When saving job-scoped rows */
    private Long reconciliationJobId;
}
