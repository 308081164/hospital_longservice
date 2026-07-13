package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerDiscount {

    private Long id;

    private Long customerId;

    private String name;

    private BigDecimal discountRate;

    private String applyStage = "after_base";

    private Boolean skipWhenFixedPrice = true;

    private String categoryFilter;

    private String productKeywordFilter;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
