package com.hospital.backend.dto.request.logistics;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogisticsCardTransactionRequest {

    @NotNull
    @Positive
    private Double amount;

    private String remark;
}
