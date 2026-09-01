package com.hospital.backend.dto.request.customer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SaveCustomerProductRuleRequest {

    private Long productId;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    private String matchMode;

    private String name;

    private Integer priority;

    private BigDecimal price;

    private List<BigDecimal> acceptedPrices;

    @DecimalMin(value = "0.01", message = "倍率不能小于 0.01")
    @DecimalMax(value = "99", message = "倍率不能大于 99")
    private BigDecimal multiplier;

    private BigDecimal fee;

    private Integer threshold;

    private BigDecimal foldRatio;

    /** 关键词匹配模式：exact_token / contains */
    private String keywordMatchMode;

    private List<String> keywords;

    private List<String> excludeKeywords;

    private List<String> materials;

    /** HT / LT / ANY */
    private String temperature;

    private Integer bagSizeEquals;

    private Integer maxBagSizeExclusive;

    private Integer minInstrumentCount;

    private Integer maxInstrumentCount;

    private Boolean skipPackaging;

    private Boolean skipDiscount;

    /** 原价匹配条件 */
    private BigDecimal originalUnitPrice;

    /** 科室条件 JSON */
    private List<String> departments;

    /** PER_PACK / PER_INSTRUMENT / PACK_NAME_SUFFIX */
    private String billingMode;

    /** EFFECTIVE_COUNT / ZSD_PER_PACK / PACK_NAME_LAST_NUMBER */
    private String pieceCountSource;

    private Boolean isActive;
}
