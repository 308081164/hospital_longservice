package com.hospital.backend.dto.response.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MatchPreviewResponse {

    private boolean matched;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("category_code")
    private String categoryCode;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("pricing_path")
    private String pricingPath;

    @JsonProperty("pricing_mode")
    private String pricingMode;

    @JsonProperty("public_price")
    private BigDecimal publicPrice;

    @JsonProperty("original_price")
    private BigDecimal originalPrice;

    @JsonProperty("matched_rule_id")
    private Long matchedRuleId;

    @JsonProperty("matched_alias")
    private String matchedAlias;

    @JsonProperty("variant_id")
    private Long variantId;

    @JsonProperty("variant_display_name")
    private String variantDisplayName;

    @JsonProperty("spec_fingerprint")
    private String specFingerprint;

    @JsonProperty("variant_public_price")
    private BigDecimal variantPublicPrice;

    private String source;
}
