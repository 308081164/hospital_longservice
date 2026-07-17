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

    @JsonProperty("trip_source")
    private String tripSource;

    @JsonProperty("allocation_mode")
    private String allocationMode;

    @JsonProperty("billing_weekdays")
    private java.util.List<Integer> billingWeekdays;

    @JsonProperty("exclude_departments")
    private java.util.List<String> excludeDepartments;

    @JsonProperty("min_charge")
    private BigDecimal minCharge;

    @JsonProperty("max_cap")
    private BigDecimal maxCap;

    @JsonProperty("base_multiplier")
    private BigDecimal baseMultiplier;

    @JsonProperty("adjusted_multiplier")
    private BigDecimal adjustedMultiplier;

    @JsonProperty("urgent_logistics_fee_per_trip")
    private BigDecimal urgentLogisticsFeePerTrip;

    @JsonProperty("urgent_logistics_discount_rate")
    private BigDecimal urgentLogisticsDiscountRate;

    @JsonProperty("monthly_amount")
    private BigDecimal monthlyAmount;

    @JsonProperty("card_deduction_enabled")
    private Boolean cardDeductionEnabled;

    @JsonProperty("card_deduct_mode")
    private String cardDeductMode;

    @JsonProperty("card_monthly_cap")
    private BigDecimal cardMonthlyCap;

    @JsonProperty("logistics_merge_group_id")
    private Long logisticsMergeGroupId;

    @JsonProperty("merge_same_day")
    private Boolean mergeSameDay;

    @JsonProperty("single_owner_customer_id")
    private Long singleOwnerCustomerId;

    @JsonProperty("apply_stage")
    private String applyStage;

    private Integer priority;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
