package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductMatchRule extends BaseEntity {

    private Long productId;

    private Long variantId;

    private String matchType;

    private String targetField;

    private String patternValue;

    private String matchFields;

    private String conditionsJson;

    private Integer priority = 100;

    private Boolean isActive = true;
}
