package com.hospital.backend.dto.request.customer;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CustomerDiscountDto {

    private Long id;

    private String name;

    /** HT / LT / ANY，默认 ANY */
    private String temperature;

    private BigDecimal discountRate;

    private String applyStage;

    /** bill_detail / settlement_only / export_only，多选时写入 policy.params.applyStages */
    private List<String> applyStages;

    private Boolean skipWhenFixedPrice;

    private Integer priority;

    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
