package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductCategory extends BaseEntity {

    private String code;

    private String name;

    private Long parentId;

    private String pricingPath;

    private Integer sortOrder = 0;

    private Boolean isActive = true;

    private LocalDateTime deletedAt;
}
