package com.hospital.backend.dto.response.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class LogisticsImportResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("job_id")
    private Long jobId;

    @JsonProperty("billing_month")
    private String billingMonth;

    @JsonProperty("trip_date")
    private LocalDate tripDate;

    private String route;

    @JsonProperty("trip_count")
    private Integer tripCount;

    @JsonProperty("fee_amount")
    private Double feeAmount;

    private String notes;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
