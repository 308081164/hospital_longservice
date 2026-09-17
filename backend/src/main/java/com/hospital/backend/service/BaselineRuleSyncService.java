package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface BaselineRuleSyncService {

    int importCustomerBaseline(Long customerId, JsonNode baselineNode, boolean dryRun);

    int importAllBaselines(boolean dryRun);

    /** 按 baseline index 同步 billing_enabled（hash 未变时也须执行，避免历史客户残留启用）。 */
    int syncBillingEnabledFromBaselineIndex();

    Map<String, Object> exportCustomer(Long customerId);

    Map<String, Object> exportAll();

    List<String> validateBaselineNode(JsonNode baselineNode);
}
