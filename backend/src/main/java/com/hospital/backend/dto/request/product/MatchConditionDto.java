package com.hospital.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchConditionDto {

    @NotBlank(message = "条件字段不能为空")
    private String field;

    @NotBlank(message = "条件运算符不能为空")
    private String operator;

    private String value;
}
