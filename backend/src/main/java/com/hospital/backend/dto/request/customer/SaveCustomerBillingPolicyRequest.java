package com.hospital.backend.dto.request.customer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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

    private BigDecimal minCharge;

    private BigDecimal maxCap;

    private Integer priority;

    private Boolean isActive;
}
