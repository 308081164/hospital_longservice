package com.hospital.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductAliasDto {

    private Long id;

    @NotBlank(message = "别名不能为空")
    private String alias;

    private String matchType;

    private Integer priority;

    private Boolean isActive;
}
