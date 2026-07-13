package com.hospital.backend.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SaveProductRequest {

    @NotNull(message = "产品分类不能为空")
    private Long categoryId;

    private String skuCode;

    @NotBlank(message = "产品名称不能为空")
    private String name;

    private String pricingMode;

    @DecimalMin(value = "0", message = "公开价格不能为负数")
    private BigDecimal publicPrice;

    @DecimalMin(value = "0", message = "原价不能为负数")
    private BigDecimal originalPrice;

    private Integer priority;

    private Boolean isActive;

    @Valid
    private List<MatchRuleDto> matchRules;

    @Valid
    private List<ProductAliasDto> aliases;
}
