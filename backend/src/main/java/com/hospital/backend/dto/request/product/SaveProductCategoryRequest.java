package com.hospital.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveProductCategoryRequest {

    @NotBlank(message = "分类编码不能为空")
    private String code;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Long parentId;

    @NotBlank(message = "计价路径不能为空")
    private String pricingPath;

    private Integer sortOrder;

    private Boolean isActive;
}
