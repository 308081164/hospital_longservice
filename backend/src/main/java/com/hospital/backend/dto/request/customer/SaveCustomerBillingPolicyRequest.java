package com.hospital.backend.dto.request.customer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SaveCustomerBillingPolicyRequest {

    @NotBlank(message = "策略类型不能为空")
    private String policyType;

    private String name;

    /** HT / LT / ANY */
    private String temperature;

    @DecimalMin(value = "0.0001", message = "折扣率必须大于 0")
    @DecimalMax(value = "1", message = "折扣率不能大于 1")
    private BigDecimal rate;

    private Boolean skipWhenFixedPrice;

    private BigDecimal feePerTrip;

    /** delivery_date | import */
    private String tripSource;

    /** none | dept_ratio | equal | proportional | single_owner | cross_hospital_merge */
    private String allocationMode;

    /** ISO weekday 1=Mon … 7=Sun */
    private List<Integer> billingWeekdays;

    private List<String> excludeDepartments;

    private BigDecimal minCharge;

    private BigDecimal maxCap;

    /** URGENT policy: default 1.25 */
    private BigDecimal baseMultiplier;

    /** URGENT policy: default 1.025 */
    private BigDecimal adjustedMultiplier;

    private BigDecimal urgentLogisticsFeePerTrip;

    private BigDecimal urgentLogisticsDiscountRate;

    /** DEDUCTION policy: fixed monthly amount */
    private BigDecimal monthlyAmount;

    /** LOGISTICS: enable logistics card balance deduction */
    private Boolean cardDeductionEnabled;

    /** LOGISTICS: auto | none */
    private String cardDeductMode;

    /** LOGISTICS: monthly cap for card deduction */
    private BigDecimal cardMonthlyCap;

    /** LOGISTICS: reference to logistics_merge customer group */
    private Long logisticsMergeGroupId;

    /** LOGISTICS: merge same-day trips across group members */
    private Boolean mergeSameDay;

    /** LOGISTICS: when allocationMode=single_owner, all group logistics attributed here */
    private Long singleOwnerCustomerId;

    /** bill_detail / settlement_only / export_only */
    private String applyStage;

    private Integer priority;

    private Boolean isActive;
}
