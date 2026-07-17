package com.hospital.backend.dto.response.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LogisticsCardTransactionResponse {

    private Long id;

    @JsonProperty("card_id")
    private Long cardId;

    @JsonProperty("transaction_type")
    private String transactionType;

    private Double amount;

    @JsonProperty("balance_after")
    private Double balanceAfter;

    @JsonProperty("job_id")
    private Long jobId;

    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
