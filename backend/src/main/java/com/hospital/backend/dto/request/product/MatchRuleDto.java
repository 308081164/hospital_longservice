package com.hospital.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class MatchRuleDto {

    private Long id;

    @NotBlank(message = "匹配类型不能为空")
    private String matchType;

    private String targetField;

    private String patternValue;

    private List<String> matchFields;

    private List<MatchConditionDto> conditions;

    private Integer priority;

    private Boolean isActive;
}
