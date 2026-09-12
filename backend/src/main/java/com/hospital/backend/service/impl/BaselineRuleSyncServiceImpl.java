package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.BaselineRuleIndex;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.service.BaselineRuleSyncService;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.PricingRuleCompileCache;
import com.hospital.backend.service.RuleChangeAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BaselineRuleSyncServiceImpl implements BaselineRuleSyncService {

    private final BaselineRuleIndex baselineRuleIndex;
    private final CustomerMapper customerMapper;
    private final CustomerProductRuleMapper productRuleMapper;
    private final RuleChangeAuditService ruleChangeAuditService;
    private final PricingRuleCompileCache compileCache;

    @Override
    @Transactional
    public int importCustomerBaseline(Long customerId, JsonNode baselineNode, boolean dryRun) {
        List<String> errors = validateBaselineNode(baselineNode);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        if (dryRun) {
            JsonNode rules = baselineNode.path("productRules");
            return rules.isArray() ? rules.size() : 0;
        }
        int count = 0;
        JsonNode rules = baselineNode.path("productRules");
        if (rules.isArray()) {
            for (JsonNode ruleNode : rules) {
                upsertProductRule(customerId, ruleNode);
                count++;
            }
        }
        int purged = purgeOrphanRules(customerId, baselineNode, dryRun);
        Customer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            compileCache.invalidateCustomer(customerId);
            ruleChangeAuditService.logChange(
                    customerId,
                    null,
                    null,
                    "IMPORT",
                    "PRODUCT_RULE",
                    null,
                    Map.of("importedRules", count, "purgedRules", purged, "customerCode", customer.getCode()),
                    "baseline-import",
                    "baseline 导入 " + count + " 条规则");
        }
        return count;
    }

    /** baseline 中不存在的规则从 DB 硬删（含已停用规则）。 */
    private int purgeOrphanRules(Long customerId, JsonNode baselineNode, boolean dryRun) {
        Set<String> baselineNames = new HashSet<>();
        JsonNode rules = baselineNode.path("productRules");
        if (rules.isArray()) {
            for (JsonNode ruleNode : rules) {
                String name = text(ruleNode, "name");
                if (name != null && !name.isBlank()) {
                    baselineNames.add(name);
                }
            }
        }
        int purged = 0;
        for (CustomerProductRule rule : productRuleMapper.selectByCustomerId(customerId)) {
            if (baselineNames.contains(rule.getName())) {
                continue;
            }
            if (!dryRun) {
                ruleChangeAuditService.logChange(
                        customerId,
                        null,
                        rule.getId(),
                        "DELETE",
                        "PRODUCT_RULE",
                        RuleQuarantineServiceImpl.snapshotRule(rule),
                        Map.of("purgedByBaselineImport", true),
                        "baseline-import",
                        "baseline 导入清除孤儿规则：" + rule.getName());
                productRuleMapper.deleteById(rule.getId());
            }
            purged++;
        }
        return purged;
    }

    @Override
    @Transactional
    public int importAllBaselines(boolean dryRun) {
        int total = 0;
        for (String code : baselineRuleIndex.customerCodes()) {
            JsonNode baseline = baselineRuleIndex.baselineForCustomer(code);
            if (baseline == null) {
                continue;
            }
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                customer = ensureCustomerFromBaseline(baseline, dryRun);
            }
            if (customer == null) {
                continue;
            }
            total += importCustomerBaseline(customer.getId(), baseline, dryRun);
        }
        return total;
    }

    /** baseline 新引入客户时，按 JSON 元数据自动建档，避免 import 因客户缺失而跳过。 */
    private Customer ensureCustomerFromBaseline(JsonNode baselineNode, boolean dryRun) {
        String code = text(baselineNode, "customerCode");
        if (code == null || code.isBlank()) {
            return null;
        }
        if (dryRun) {
            Customer stub = new Customer();
            stub.setCode(code);
            stub.setId(-1L);
            return stub;
        }
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(text(baselineNode, "customerName", code));
        customer.setStatus("active");
        customer.setBillingEnabled(bool(baselineNode, "billingEnabled", true));
        customer.setBillingPricingMode(text(baselineNode, "billingPricingMode", "standard"));
        customerMapper.insert(customer);
        return customer;
    }

    @Override
    public Map<String, Object> exportCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerCode", customer.getCode());
        payload.put("customerName", customer.getCanonicalName());
        payload.put("billingEnabled", customer.getBillingEnabled());
        payload.put("billingPricingMode", customer.getBillingPricingMode());
        List<Map<String, Object>> rules = productRuleMapper.selectByCustomerId(customerId).stream()
                .map(RuleQuarantineServiceImpl::snapshotRule)
                .toList();
        payload.put("productRules", rules);
        return payload;
    }

    @Override
    public Map<String, Object> exportAll() {
        Map<String, Object> all = new LinkedHashMap<>();
        for (String code : baselineRuleIndex.customerCodes()) {
            Customer customer = customerMapper.selectByCode(code);
            if (customer != null) {
                all.put(code, exportCustomer(customer.getId()));
            }
        }
        all.put("baseline_hash", baselineRuleIndex.baselineHash());
        all.put("exported_at", java.time.Instant.now().toString());
        return all;
    }

    @Override
    public List<String> validateBaselineNode(JsonNode baselineNode) {
        List<String> errors = new ArrayList<>();
        if (baselineNode == null || baselineNode.isMissingNode()) {
            errors.add("baseline 为空");
            return errors;
        }
        if (!baselineNode.hasNonNull("customerCode")) {
            errors.add("缺少 customerCode");
        }
        JsonNode rules = baselineNode.path("productRules");
        if (!rules.isArray()) {
            errors.add("productRules 必须为数组");
            return errors;
        }
        for (int i = 0; i < rules.size(); i++) {
            JsonNode rule = rules.get(i);
            if (!rule.hasNonNull("name") || rule.get("name").asText().isBlank()) {
                errors.add("第 " + (i + 1) + " 条规则缺少 name");
            }
            if (!rule.hasNonNull("ruleType")) {
                errors.add("第 " + (i + 1) + " 条规则缺少 ruleType");
            }
            if ("FIXED_PRICE".equals(rule.path("ruleType").asText())) {
                if (rule.has("isActive") && !rule.get("isActive").asBoolean(true)) {
                    continue;
                }
                if (!rule.path("skipPackaging").asBoolean(false) || !rule.path("skipDiscount").asBoolean(false)) {
                    errors.add("FIXED_PRICE「" + rule.path("name").asText() + "」须 skipPackaging+skipDiscount");
                }
            }
        }
        return errors;
    }

    private void upsertProductRule(Long customerId, JsonNode ruleNode) {
        String name = text(ruleNode, "name");
        if (name == null || name.isBlank()) {
            return;
        }
        CustomerProductRule rule = findProductRuleByName(customerId, name);
        boolean insert = rule == null;
        if (insert) {
            rule = new CustomerProductRule();
            rule.setCustomerId(customerId);
            rule.setName(name);
        }
        rule.setRuleType(text(ruleNode, "ruleType", "FIXED_PRICE"));
        rule.setBillingMode(textOrNull(ruleNode, "billingMode"));
        if (ruleNode.hasNonNull("pieceCountSource")) {
            rule.setPieceCountSource(text(ruleNode, "pieceCountSource"));
        }
        rule.setPriority(intVal(ruleNode, "priority", 100));
        rule.setPrice(ruleNode.hasNonNull("price") ? decimal(ruleNode, "price") : null);
        rule.setFee(ruleNode.hasNonNull("fee") ? decimal(ruleNode, "fee") : null);
        if (ruleNode.has("materials")) {
            rule.setMaterials(toJsonArray(ruleNode.get("materials")));
        }
        if (ruleNode.hasNonNull("foldRatio")) {
            rule.setFoldRatio(decimal(ruleNode, "foldRatio"));
        } else {
            rule.setFoldRatio(null);
        }
        if (ruleNode.has("threshold") && !ruleNode.get("threshold").isNull()) {
            rule.setThreshold(intVal(ruleNode, "threshold", null));
        } else {
            rule.setThreshold(null);
        }
        if (ruleNode.has("extraCount")) {
            rule.setExtraCount(ruleNode.get("extraCount").isNull() ? null : intVal(ruleNode, "extraCount", null));
        }
        if (ruleNode.has("keywords")) {
            rule.setKeywords(toJsonArray(ruleNode.get("keywords")));
        }
        if (ruleNode.has("excludeKeywords")) {
            rule.setExcludeKeywords(toJsonArray(ruleNode.get("excludeKeywords")));
        }
        if (ruleNode.hasNonNull("temperature")) {
            rule.setTemperature(text(ruleNode, "temperature"));
        }
        if (ruleNode.hasNonNull("bagSizeEquals")) {
            rule.setBagSizeEquals(intVal(ruleNode, "bagSizeEquals", null));
        }
        if (ruleNode.hasNonNull("maxBagSizeExclusive")) {
            rule.setMaxBagSizeExclusive(intVal(ruleNode, "maxBagSizeExclusive", null));
        }
        if (ruleNode.hasNonNull("minInstrumentCount")) {
            rule.setMinInstrumentCount(intVal(ruleNode, "minInstrumentCount", null));
        }
        if (ruleNode.hasNonNull("maxInstrumentCount")) {
            rule.setMaxInstrumentCount(intVal(ruleNode, "maxInstrumentCount", null));
        }
        rule.setSkipPackaging(bool(ruleNode, "skipPackaging", false));
        rule.setSkipDiscount(bool(ruleNode, "skipDiscount", false));
        rule.setMatchMode(text(ruleNode, "matchMode", "first"));
        rule.setKeywordMatchMode(defaultKeywordMatchMode(textOrNull(ruleNode, "keywordMatchMode")));
        if (ruleNode.has("acceptedPrices")) {
            rule.setAcceptedPrices(ruleNode.get("acceptedPrices").toString());
        }
        String conditionsJson = null;
        if (ruleNode.hasNonNull("conditionsJson")) {
            JsonNode conditionsNode = ruleNode.get("conditionsJson");
            conditionsJson = conditionsNode.isTextual() ? conditionsNode.asText() : conditionsNode.toString();
        }
        if (ruleNode.has("acceptedTypes")) {
            conditionsJson = BillingConditionEvaluator.mergeAcceptedTypesIntoConditions(
                    conditionsJson, ruleNode.get("acceptedTypes"));
        }
        rule.setConditionsJson(JsonUtils.canonicalJsonText(conditionsJson));
        if (ruleNode.has("isActive")) {
            rule.setIsActive(bool(ruleNode, "isActive", true));
        } else if (insert) {
            rule.setIsActive(true);
        }
        if (insert) {
            productRuleMapper.insert(rule);
        } else {
            productRuleMapper.updateById(rule);
        }
    }

    private CustomerProductRule findProductRuleByName(Long customerId, String ruleName) {
        return productRuleMapper.selectByCustomerId(customerId).stream()
                .filter(r -> ruleName.equals(r.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String toJsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return JsonUtils.toJson(values);
    }

    private static String defaultKeywordMatchMode(String mode) {
        return mode == null || mode.isBlank() ? "exact_token" : mode;
    }

    private static String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText();
    }

    private static boolean bool(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field)) {
            return defaultValue;
        }
        return node.get(field).asBoolean(defaultValue);
    }

    private static Integer intVal(JsonNode node, String field, Integer defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return new BigDecimal(node.get(field).asText());
    }
}
