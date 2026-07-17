package com.hospital.backend.dto.request.logistics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveLogisticsCardRequest {

    @NotNull
    private Long customerId;

    @NotBlank
    private String name;

    private Double initialBalance;

    private Boolean isActive = true;
}
