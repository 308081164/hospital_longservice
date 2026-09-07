package com.hospital.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.CustomerProductRuleTombstone;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.CustomerProductRuleTombstoneMapper;
import com.hospital.backend.service.RuleChangeAuditService;
import com.hospital.backend.service.RuleQuarantineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleQuarantineServiceImpl implements RuleQuarantineService {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final CustomerProductRuleMapper productRuleMapper;
    private final CustomerProductRuleTombstoneMapper tombstoneMapper;
    private final RuleChangeAuditService ruleChangeAuditService;

    @Override
    @Transactional
    public boolean quarantineRule(
            Customer customer,
            CustomerProductRule rule,
            String manifestHash,
            String reason,
            String operatorName) {
        if (rule == null || rule.getId() == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(rule.getIsActive())) {
            return false;
        }
        Map<String, Object> before = snapshotRule(rule);
        rule.setIsActive(false);
        productRuleMapper.updateById(rule);

        CustomerProductRuleTombstone tombstone = new CustomerProductRuleTombstone();
        tombstone.setProductRuleId(rule.getId());
        tombstone.setCustomerId(rule.getCustomerId());
        tombstone.setCustomerCode(customer != null ? customer.getCode() : null);
        tombstone.setRuleName(rule.getName());
        tombstone.setRuleSnapshot(JsonUtils.toJson(before));
        tombstone.setManifestHash(manifestHash);
        tombstone.setQuarantineReason(reason);
        tombstoneMapper.insert(tombstone);

        ruleChangeAuditService.logChange(
                rule.getCustomerId(),
                null,
                rule.getId(),
                "QUARANTINE",
                "PRODUCT_RULE",
                before,
                Map.of("isActive", false, "quarantineReason", reason),
                operatorName != null ? operatorName : "manifest-reconciler",
                "规则已隔离（非 manifest）：" + rule.getName());

        log.warn(
                "【计价规则隔离】客户={} 规则={} 关键词={} 原因={}",
                customer != null ? customer.getCode() : rule.getCustomerId(),
                rule.getName(),
                rule.getKeywords(),
                reason);
        return true;
    }

    @Override
    public List<CustomerProductRuleTombstone> listActiveQuarantines(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return tombstoneMapper.selectActiveByCustomerId(customerId);
    }

    @Override
    public List<CustomerProductRuleTombstone> listAllActiveQuarantines(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return tombstoneMapper.selectAllActive(safeLimit);
    }

    @Override
    @Transactional
    public boolean restoreQuarantine(Long tombstoneId, String operatorName) {
        CustomerProductRuleTombstone tombstone = tombstoneMapper.selectById(tombstoneId);
        if (tombstone == null || tombstone.getRestoredAt() != null) {
            return false;
        }
        CustomerProductRule rule = productRuleMapper.selectById(tombstone.getProductRuleId());
        if (rule == null) {
            return false;
        }
        Map<String, Object> before = snapshotRule(rule);
        rule.setIsActive(true);
        productRuleMapper.updateById(rule);
        tombstoneMapper.markRestored(tombstoneId, operatorName != null ? operatorName : "cli-restore");

        ruleChangeAuditService.logChange(
                rule.getCustomerId(),
                null,
                rule.getId(),
                "RESTORE",
                "PRODUCT_RULE",
                before,
                Map.of("isActive", true),
                operatorName != null ? operatorName : "cli-restore",
                "规则已从隔离恢复：" + rule.getName());
        return true;
    }

    @Override
    public long countActiveQuarantines() {
        return tombstoneMapper.countActive();
    }

    @Override
    public CustomerProductRuleTombstone getTombstone(Long tombstoneId) {
        if (tombstoneId == null) {
            return null;
        }
        return tombstoneMapper.selectById(tombstoneId);
    }

    @Override
    public Map<String, Object> buildSeedPatchDraft(CustomerProductRuleTombstone tombstone) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("_comment", "将下方 productRules 条目合并到对应客户种子 JSON，再运行 billing_rules_manifest.py --write");
        if (tombstone.getCustomerCode() != null) {
            draft.put("customerCode", tombstone.getCustomerCode());
        }
        draft.put("productRule", parseSnapshot(tombstone.getRuleSnapshot()));
        draft.put("tombstoneId", tombstone.getId());
        draft.put("quarantinedAt", tombstone.getQuarantinedAt());
        return draft;
    }

    static Map<String, Object> snapshotRule(CustomerProductRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("customerId", rule.getCustomerId());
        map.put("name", rule.getName());
        map.put("ruleType", rule.getRuleType());
        map.put("billingMode", rule.getBillingMode());
        map.put("pieceCountSource", rule.getPieceCountSource());
        map.put("matchMode", rule.getMatchMode());
        map.put("priority", rule.getPriority());
        map.put("productId", rule.getProductId());
        map.put("variantId", rule.getVariantId());
        map.put("keywords", rule.getKeywords());
        map.put("excludeKeywords", rule.getExcludeKeywords());
        map.put("materials", rule.getMaterials());
        map.put("temperature", rule.getTemperature());
        map.put("bagSizeEquals", rule.getBagSizeEquals());
        map.put("minBagSizeInclusive", rule.getMinBagSizeInclusive());
        map.put("maxBagSizeExclusive", rule.getMaxBagSizeExclusive());
        map.put("minInstrumentCount", rule.getMinInstrumentCount());
        map.put("maxInstrumentCount", rule.getMaxInstrumentCount());
        map.put("price", rule.getPrice());
        map.put("originalUnitPrice", rule.getOriginalUnitPrice());
        map.put("acceptedPrices", rule.getAcceptedPrices());
        map.put("fee", rule.getFee());
        map.put("multiplier", rule.getMultiplier());
        map.put("threshold", rule.getThreshold());
        map.put("foldRatio", rule.getFoldRatio());
        map.put("keywordMatchMode", rule.getKeywordMatchMode());
        map.put("extraCount", rule.getExtraCount());
        map.put("skipPackaging", rule.getSkipPackaging());
        map.put("skipDiscount", rule.getSkipDiscount());
        map.put("conditionsJson", rule.getConditionsJson());
        map.put("isActive", rule.getIsActive());
        return map;
    }

    private static Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
