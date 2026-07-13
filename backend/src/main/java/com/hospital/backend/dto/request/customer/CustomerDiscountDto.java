package com.hospital.backend.dto.request.customer;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CustomerDiscountDto {

    private Long id;

    private String name;

    /** HT / LT / ANY，默认 ANY */
    private String temperature;

    private BigDecimal discountRate;

    private String applyStage;

    private Boolean skipWhenFixedPrice;

    private Integer priority;

    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
