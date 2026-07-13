package com.hospital.backend.dto.response.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductCategoryResponse {

    private Long id;

    private String code;

    private String name;

    @JsonProperty("parent_id")
    private Long parentId;

    @JsonProperty("pricing_path")
    private String pricingPath;

    @JsonProperty("sort_order")
    private Integer sortOrder;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("product_count")
    private Long productCount;

    @JsonProperty("child_count")
    private Long childCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
