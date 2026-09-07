package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface BaselineRuleSyncService {

    int importCustomerBaseline(Long customerId, JsonNode baselineNode, boolean dryRun);

    int importAllBaselines(boolean dryRun);

    Map<String, Object> exportCustomer(Long customerId);

    Map<String, Object> exportAll();

    List<String> validateBaselineNode(JsonNode baselineNode);
}
