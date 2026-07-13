package com.hospital.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuickOnboardRequest {

    @NotBlank(message = "产品族名不能为空")
    private String familyName;

    @NotBlank(message = "包名不能为空")
    private String packName;

    private String type;

    private String packageMaterial;

    private String categoryCode;

    private Long categoryId;

    private BigDecimal publicPrice;

    private String pricingMode;

    /** 可选：同时创建客户特例规则 */
    private Long customerId;

    private String customerRuleType;

    private BigDecimal customerPrice;
}
