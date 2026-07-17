package com.hospital.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.response.billing.BillingRuleChangeLogResponse;
import com.hospital.backend.entity.BillingRuleChangeLog;
import com.hospital.backend.mapper.BillingRuleChangeLogMapper;
import com.hospital.backend.service.RuleChangeAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleChangeAuditServiceImpl implements RuleChangeAuditService {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final BillingRuleChangeLogMapper changeLogMapper;

    @Override
    public void logChange(
            Long customerId,
            Long ruleGroupId,
            Long productRuleId,
            String changeType,
            String entityType,
            Map<String, Object> before,
            Map<String, Object> after,
            String operatorName,
            String summary) {
        BillingRuleChangeLog entry = new BillingRuleChangeLog();
        entry.setCustomerId(customerId);
        entry.setRuleGroupId(ruleGroupId);
        entry.setProductRuleId(productRuleId);
        entry.setChangeType(changeType);
        entry.setEntityType(entityType != null ? entityType : "PRODUCT_RULE");
        entry.setBeforeSnapshot(toJson(before));
        entry.setAfterSnapshot(toJson(after));
        entry.setOperatorName(operatorName);
        entry.setChangeSummary(summary);
        changeLogMapper.insert(entry);
    }

    @Override
    public List<BillingRuleChangeLogResponse> listRecentChanges(Long customerId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return changeLogMapper.selectByCustomerId(customerId, safeLimit).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private BillingRuleChangeLogResponse toResponse(BillingRuleChangeLog log) {
        return BillingRuleChangeLogResponse.builder()
                .id(log.getId())
                .customerId(log.getCustomerId())
                .changeType(log.getChangeType())
                .entityType(log.getEntityType())
                .changeSummary(log.getChangeSummary())
                .operatorName(log.getOperatorName())
                .createdAt(log.getCreatedAt())
                .beforeSnapshot(parseJson(log.getBeforeSnapshot()))
                .afterSnapshot(parseJson(log.getAfterSnapshot()))
                .build();
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("审计快照序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
