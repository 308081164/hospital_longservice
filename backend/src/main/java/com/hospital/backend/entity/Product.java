package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class Product extends BaseEntity {

    private Long categoryId;

    private String skuCode;

    private String name;

    private String pricingMode;

    /** 公开价格（无客户专属规则时的默认单价） */
    private BigDecimal publicPrice;

    /** 原价（展示/对比用，可选） */
    private BigDecimal originalPrice;

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime deletedAt;
}
