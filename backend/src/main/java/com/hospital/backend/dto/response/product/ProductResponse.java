package com.hospital.backend.dto.response.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hospital.backend.dto.request.product.MatchRuleDto;
import com.hospital.backend.dto.request.product.ProductAliasDto;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProductResponse {

    private Long id;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("category_code")
    private String categoryCode;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("sku_code")
    private String skuCode;

    private String name;

    @JsonProperty("pricing_mode")
    private String pricingMode;

    @JsonProperty("pricing_path")
    private String pricingPath;

    @JsonProperty("public_price")
    private BigDecimal publicPrice;

    @JsonProperty("original_price")
    private BigDecimal originalPrice;

    private Integer priority;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("match_rules")
    private List<MatchRuleDto> matchRules;

    private List<ProductAliasDto> aliases;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
