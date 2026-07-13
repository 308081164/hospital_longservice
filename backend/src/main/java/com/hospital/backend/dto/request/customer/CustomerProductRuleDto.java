package com.hospital.backend.dto.request.customer;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CustomerProductRuleDto {

    private Long id;

    private String ruleType;

    private String matchMode;

    private String name;

    private Integer priority;

    private Long productId;

    private List<String> keywords;

    private List<String> excludeKeywords;

    private List<String> materials;

    private String temperature;

    private Integer bagSizeEquals;

    private Integer maxBagSizeExclusive;

    private Integer minInstrumentCount;

    private Integer maxInstrumentCount;

    private BigDecimal price;

    private List<BigDecimal> acceptedPrices;

    private BigDecimal fee;

    private BigDecimal multiplier;

    private String productName;

    private Integer threshold;

    private BigDecimal foldRatio;

    private Boolean skipPackaging;

    private Boolean skipDiscount;

    private Boolean isActive;
}
