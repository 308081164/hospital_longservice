package com.hospital.backend.dto.response.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BillingRuleSimulateResponse {

    @JsonProperty("expected_unit_price")
    private Double expectedUnitPrice;

    @JsonProperty("corrected_total_price")
    private Double correctedTotalPrice;

    private Double difference;

    private String status;

    @JsonProperty("pricing_rule")
    private String pricingRule;

    @JsonProperty("matched_rule_id")
    private Long matchedRuleId;

    @JsonProperty("matched_price_option")
    private Double matchedPriceOption;

    private List<String> notes;

    @JsonProperty("policy_traces")
    private List<String> policyTraces;

    @JsonProperty("match_chain")
    private List<Map<String, Object>> matchChain;
}
