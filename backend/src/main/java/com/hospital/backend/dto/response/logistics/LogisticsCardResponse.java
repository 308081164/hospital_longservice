package com.hospital.backend.dto.response.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class LogisticsCardResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    private String name;

    private Double balance;

    @JsonProperty("initial_balance")
    private Double initialBalance;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private List<LogisticsCardTransactionResponse> transactions;
}
