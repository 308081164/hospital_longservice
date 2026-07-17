package com.hospital.backend.dto.request.logistics;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SaveLogisticsImportRequest {

    private Long jobId;

    private String billingMonth;

    @NotNull
    private LocalDate tripDate;

    private String route;

    private Integer tripCount = 1;

    private Double feeAmount;

    private String notes;
}
