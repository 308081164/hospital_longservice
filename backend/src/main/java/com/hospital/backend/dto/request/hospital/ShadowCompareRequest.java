package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ShadowCompareRequest {

    @NotNull(message = "生产规则ID不能为空")
    private Long productionRuleId;

    private Long draftRuleId;

    private Map<String, Object> draftRules;

    private String hospitalName;

    @NotNull(message = "样本行不能为空")
    private List<Map<String, Object>> sampleRows;
}
