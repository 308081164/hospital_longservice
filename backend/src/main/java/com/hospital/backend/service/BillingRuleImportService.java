package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.billing.BillingRuleImportConfirmRequest;
import com.hospital.backend.dto.request.billing.BillingRuleImportPreviewRequest;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingRuleImportService {

    private final CustomerMapper customerMapper;
    private final CustomerProductRuleMapper productRuleMapper;
    private final BillingRuleGroupSyncService groupSyncService;
    private final RuleChangeAuditService auditService;

    public Result<Map<String, Object>> previewImport(BillingRuleImportPreviewRequest request) {
        Customer customer = customerMapper.selectById(request.getCustomerId());
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        List<Map<String, Object>> parsed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, Object> raw = request.getRows().get(i);
            try {
                parsed.add(parseRow(raw));
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 行: " + e.getMessage());
            }
        }
        Result<Map<String, Object>> conflictResult = groupSyncService.detectConflicts(parsed);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("previewRules", parsed);
        data.put("errors", errors);
        data.put("validCount", parsed.size());
        if (conflictResult.getData() != null) {
            data.putAll(conflictResult.getData());
        }
        return Result.success(data);
    }

    @Transactional
    public Result<Map<String, Object>> confirmImport(BillingRuleImportConfirmRequest request) {
        Customer customer = customerMapper.selectById(request.getCustomerId());
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        int imported = 0;
        for (Map<String, Object> raw : request.getRows()) {
            CustomerProductRule rule = toEntity(parseRow(raw), request.getCustomerId());
            productRuleMapper.insert(rule);
            imported++;
        }
        groupSyncService.syncDefaultGroupFromProductRules(request.getCustomerId(), request.getOperatorName());
        auditService.logChange(request.getCustomerId(), null, null, "IMPORT", "PRODUCT_RULE",
                null, Map.of("importedCount", imported), request.getOperatorName(),
                "批量导入 " + imported + " 条规则");
        return Result.success(Map.of("importedCount", imported));
    }

    private Map<String, Object> parseRow(Map<String, Object> raw) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleType", require(raw, "ruleType"));
        rule.put("name", raw.getOrDefault("name", raw.get("ruleName")));
        rule.put("priority", intOrDefault(raw, "priority", 100));
        putIfPresent(rule, "productId", raw.get("productId"));
        putIfPresent(rule, "variantId", raw.get("variantId"));
        putIfPresent(rule, "keywords", raw.get("keywords"));
        putIfPresent(rule, "temperature", raw.get("temperature"));
        putIfPresent(rule, "price", raw.get("price"));
        putIfPresent(rule, "fee", raw.get("fee"));
        putIfPresent(rule, "multiplier", raw.get("multiplier"));
        rule.put("isActive", boolOrDefault(raw, "isActive", true));
        return rule;
    }

    private CustomerProductRule toEntity(Map<String, Object> parsed, Long customerId) {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setCustomerId(customerId);
        rule.setRuleType(String.valueOf(parsed.get("ruleType")));
        Object name = parsed.get("name");
        rule.setName(name != null ? String.valueOf(name) : "导入规则");
        rule.setPriority(intOrDefault(parsed, "priority", 100));
        if (parsed.get("productId") != null) {
            rule.setProductId(Long.valueOf(String.valueOf(parsed.get("productId"))));
        }
        if (parsed.get("variantId") != null) {
            rule.setVariantId(Long.valueOf(String.valueOf(parsed.get("variantId"))));
        }
        if (parsed.get("keywords") != null) {
            rule.setKeywords(String.valueOf(parsed.get("keywords")));
        }
        if (parsed.get("temperature") != null) {
            rule.setTemperature(String.valueOf(parsed.get("temperature")));
        }
        if (parsed.get("price") != null) {
            rule.setPrice(new BigDecimal(String.valueOf(parsed.get("price"))));
        }
        if (parsed.get("fee") != null) {
            rule.setFee(new BigDecimal(String.valueOf(parsed.get("fee"))));
        }
        if (parsed.get("multiplier") != null) {
            rule.setMultiplier(new BigDecimal(String.valueOf(parsed.get("multiplier"))));
        }
        rule.setIsActive(boolOrDefault(parsed, "isActive", true));
        return rule;
    }

    private static String require(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: " + key);
        }
        return String.valueOf(v);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private static int intOrDefault(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v == null) {
            return defaultVal;
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static boolean boolOrDefault(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v == null) {
            return defaultVal;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
