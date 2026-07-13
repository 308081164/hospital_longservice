package com.hospital.backend.dto.response.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CustomerBillingPolicyResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("policy_type")
    private String policyType;

    private String name;

    /** HT / LT / ANY */
    private String temperature;

    private BigDecimal rate;

    @JsonProperty("skip_when_fixed_price")
    private Boolean skipWhenFixedPrice;

    @JsonProperty("fee_per_trip")
    private BigDecimal feePerTrip;

    @JsonProperty("min_charge")
    private BigDecimal minCharge;

    @JsonProperty("max_cap")
    private BigDecimal maxCap;

    private Integer priority;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
