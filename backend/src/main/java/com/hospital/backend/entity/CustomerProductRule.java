package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerProductRule {

    private Long id;

    private Long customerId;

    private String ruleType;

    private String matchMode = "first";

    private String name;

    private Integer priority = 100;

    private Long productId;

    private Long variantId;

    private String keywords;

    private String excludeKeywords;

    private String materials;

    /** HT / LT / ANY */
    private String temperature;

    private Integer bagSizeEquals;

    private Integer maxBagSizeExclusive;

    private Integer minInstrumentCount;

    private Integer maxInstrumentCount;

    private BigDecimal price;

    private String acceptedPrices;

    private BigDecimal fee;

    private BigDecimal multiplier;

    private Integer threshold;

    private BigDecimal foldRatio;

    /** 原价匹配条件（FR-M3-15） */
    private BigDecimal originalUnitPrice;

    /** JSON: [{ "field": "department", "value": ["手术室"] }] */
    private String conditionsJson;

    private Boolean skipPackaging = false;

    private Boolean skipDiscount = false;

    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
