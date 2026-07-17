package com.hospital.backend.dto.response.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class BillingRuleChangeLogResponse {

    private Long id;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("change_type")
    private String changeType;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("change_summary")
    private String changeSummary;

    @JsonProperty("operator_name")
    private String operatorName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("before_snapshot")
    private Map<String, Object> beforeSnapshot;

    @JsonProperty("after_snapshot")
    private Map<String, Object> afterSnapshot;
}
