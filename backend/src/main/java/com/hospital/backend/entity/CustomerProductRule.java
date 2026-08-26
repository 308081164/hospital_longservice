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

    private Integer minBagSizeInclusive;

    private Integer maxBagSizeExclusive;

    private Integer minInstrumentCount;

    private Integer maxInstrumentCount;

    private BigDecimal price;

    private String acceptedPrices;

    private BigDecimal fee;

    private BigDecimal multiplier;

    private Integer threshold;

    private BigDecimal foldRatio;

    /** 折算后额外加计件数（如"针N盒1"的盒固定计 1 件，不参与 5 合 1 折算） */
    private Integer extraCount;

    /** 原价匹配条件（FR-M3-15） */
    private BigDecimal originalUnitPrice;

    /** PER_PACK / PER_INSTRUMENT / PACK_NAME_SUFFIX */
    private String billingMode;

    /** EFFECTIVE_COUNT / ZSD_PER_PACK / PACK_NAME_LAST_NUMBER */
    private String pieceCountSource;

    /** JSON: [{ "field": "department", "value": ["手术室"] }] */
    private String conditionsJson;

    private Boolean skipPackaging = false;

    private Boolean skipDiscount = false;

    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
