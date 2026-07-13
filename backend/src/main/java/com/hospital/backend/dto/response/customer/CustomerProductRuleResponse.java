package com.hospital.backend.dto.response.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CustomerProductRuleResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("rule_type")
    private String ruleType;

    @JsonProperty("match_mode")
    private String matchMode;

    private String name;

    private Integer priority;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    private List<String> keywords;

    @JsonProperty("exclude_keywords")
    private List<String> excludeKeywords;

    private List<String> materials;

    @JsonProperty("temperature")
    private String temperature;

    @JsonProperty("bag_size_equals")
    private Integer bagSizeEquals;

    @JsonProperty("max_bag_size_exclusive")
    private Integer maxBagSizeExclusive;

    @JsonProperty("min_instrument_count")
    private Integer minInstrumentCount;

    @JsonProperty("max_instrument_count")
    private Integer maxInstrumentCount;

    private BigDecimal price;

    @JsonProperty("accepted_prices")
    private List<BigDecimal> acceptedPrices;

    @JsonProperty("fixed_price")
    private BigDecimal fixedPrice;

    private BigDecimal multiplier;

    private BigDecimal fee;

    @JsonProperty("fold_ratio")
    private BigDecimal foldRatio;

    private Integer threshold;

    @JsonProperty("skip_packaging")
    private Boolean skipPackaging;

    @JsonProperty("skip_discount")
    private Boolean skipDiscount;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
