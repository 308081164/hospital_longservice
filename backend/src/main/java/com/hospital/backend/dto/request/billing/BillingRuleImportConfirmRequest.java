package com.hospital.backend.dto.request.billing;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BillingRuleImportConfirmRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private List<Map<String, Object>> rows;

    private String operatorName;
}
