package com.hospital.backend.dto.response.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PricingRuleRevisionResponse {

    private Long id;

    @JsonProperty("rule_id")
    private Long ruleId;

    private String version;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
