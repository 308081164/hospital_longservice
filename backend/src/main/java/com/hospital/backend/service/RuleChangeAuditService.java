package com.hospital.backend.service;

import com.hospital.backend.dto.response.billing.BillingRuleChangeLogResponse;

import java.util.Map;

public interface RuleChangeAuditService {

    void logChange(
            Long customerId,
            Long ruleGroupId,
            Long productRuleId,
            String changeType,
            String entityType,
            Map<String, Object> before,
            Map<String, Object> after,
            String operatorName,
            String summary);

    java.util.List<BillingRuleChangeLogResponse> listRecentChanges(Long customerId, int limit);
}
