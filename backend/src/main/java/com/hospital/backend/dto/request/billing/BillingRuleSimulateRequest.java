package com.hospital.backend.dto.request.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class BillingRuleSimulateRequest {

    @NotNull
    private Long customerId;

    private Long ruleId;

    @NotBlank
    private String hospitalName;

    @NotNull
    private Map<String, Object> sampleRow;

    private String operatorName;
}
