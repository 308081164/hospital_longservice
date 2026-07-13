package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductVariant extends BaseEntity {

    private Long productId;

    private String skuCode;

    private String specFingerprint;

    private String packName;

    private String type;

    private String packageMaterial;

    private String categoryNo;

    private String familyNameParsed;

    private String specSuffix;

    private Integer instrumentCountHint;

    private String orderNoPattern;

    private String bagMaterialClass;

    private String bagTempClass;

    private Integer bagWidthMm;

    private Integer bagHeightMm;

    private String bagSizeLabel;

    private String displayName;

    private BigDecimal publicPrice;

    private BigDecimal originalPrice;

    private Integer priceSampleCount = 0;

    private Integer occurrenceCount = 0;

    private Boolean isActive = true;

    private LocalDateTime deletedAt;
}
