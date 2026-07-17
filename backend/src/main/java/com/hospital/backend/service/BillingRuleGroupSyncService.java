package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerBillingRuleGroup;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerBillingRuleGroupMapper;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingRuleGroupSyncService {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final CustomerBillingRuleGroupMapper ruleGroupMapper;
    private final CustomerProductRuleMapper productRuleMapper;
    private final CustomerMapper customerMapper;
    private final RuleChangeAuditService auditService;

    /**
     * 双写过渡：将 customer_product_rule 快照同步至默认规则组。
     */
    @Transactional
    public void syncDefaultGroupFromProductRules(Long customerId, String operatorName) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            return;
        }
        List<CustomerProductRule> rules = productRuleMapper.selectByCustomerId(customerId);
        ObjectNode snapshot = MAPPER.createObjectNode();
        ArrayNode rulesArray = MAPPER.createArrayNode();
        for (CustomerProductRule rule : rules) {
            ObjectNode node = MAPPER.createObjectNode();
            if (rule.getId() != null) {
                node.put("id", rule.getId());
            }
            node.put("ruleType", rule.getRuleType());
            node.put("name", rule.getName());
            node.put("priority", rule.getPriority() != null ? rule.getPriority() : 100);
            if (rule.getProductId() != null) {
                node.put("productId", rule.getProductId());
            }
            if (rule.getVariantId() != null) {
                node.put("variantId", rule.getVariantId());
            }
            if (rule.getKeywords() != null) {
                node.put("keywords", rule.getKeywords());
            }
            if (rule.getPrice() != null) {
                node.put("price", rule.getPrice().doubleValue());
            }
            node.put("isActive", Boolean.TRUE.equals(rule.getIsActive()));
            rulesArray.add(node);
        }
        snapshot.set("productRules", rulesArray);
        snapshot.put("syncedAt", java.time.Instant.now().toString());

        CustomerBillingRuleGroup existing = ruleGroupMapper.selectByCustomerIdAndCode(customerId, "default");
        String json = snapshot.toString();
        if (existing == null) {
            CustomerBillingRuleGroup group = new CustomerBillingRuleGroup();
            group.setCustomerId(customerId);
            group.setGroupCode("default");
            group.setGroupName("默认规则组");
            group.setRulesJson(json);
            group.setPriority(100);
            group.setIsActive(true);
            ruleGroupMapper.insert(group);
            auditService.logChange(customerId, group.getId(), null, "CREATE", "RULE_GROUP",
                    null, Map.of("rulesCount", rules.size()), operatorName, "创建默认规则组快照");
        } else {
            Map<String, Object> before = Map.of("rulesCount", countRules(existing.getRulesJson()));
            existing.setRulesJson(json);
            ruleGroupMapper.updateById(existing);
            auditService.logChange(customerId, existing.getId(), null, "UPDATE", "RULE_GROUP",
                    before, Map.of("rulesCount", rules.size()), operatorName, "同步默认规则组快照");
        }
    }

    public Result<List<Map<String, Object>>> listBuiltinTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        templates.add(template("sheng_er_standard", "省二院标准包规则", "FIXED_PRICE", "省二南岗/松北常用固定价"));
        templates.add(template("taiping_export_discount", "太平导出折扣", "DISCOUNT", "导出阶段折扣策略模板"));
        templates.add(template("hongshi_extra_fee", "红十字加收", "EXTRA_FEE", "FOLD/EXTRA_FEE 结算规则"));
        return Result.success(templates);
    }

    @Transactional
    public Result<Map<String, Object>> copyRulesFromCustomer(
            Long targetCustomerId,
            Long sourceCustomerId,
            String operatorName) {
        if (targetCustomerId.equals(sourceCustomerId)) {
            return Result.fail(400, "源客户与目标客户不能相同");
        }
        Customer target = customerMapper.selectById(targetCustomerId);
        Customer source = customerMapper.selectById(sourceCustomerId);
        if (target == null || source == null) {
            return Result.fail(404, "客户不存在");
        }

        List<CustomerProductRule> sourceRules = productRuleMapper.selectByCustomerId(sourceCustomerId);
        productRuleMapper.deleteByCustomerId(targetCustomerId);
        int copied = 0;
        for (CustomerProductRule src : sourceRules) {
            CustomerProductRule copy = cloneRule(src, targetCustomerId);
            productRuleMapper.insert(copy);
            copied++;
        }
        syncDefaultGroupFromProductRules(targetCustomerId, operatorName);
        auditService.logChange(targetCustomerId, null, null, "COPY", "PRODUCT_RULE",
                Map.of("sourceCustomerId", sourceCustomerId),
                Map.of("copiedCount", copied),
                operatorName,
                "从「" + source.getCanonicalName() + "」复制 " + copied + " 条规则");
        return Result.success(Map.of("copiedCount", copied, "sourceCustomerId", sourceCustomerId));
    }

    public Result<Map<String, Object>> detectConflicts(List<Map<String, Object>> rules) {
        Map<String, List<Integer>> signatureIndex = new LinkedHashMap<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Map<String, Object> rule = rules.get(i);
            String sig = BillingConditionEvaluator.matchSignature(rule);
            if (sig.replace("|", "").isBlank()) {
                continue;
            }
            signatureIndex.computeIfAbsent(sig, k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<String, List<Integer>> entry : signatureIndex.entrySet()) {
            if (entry.getValue().size() > 1) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("signature", entry.getKey());
                conflict.put("ruleIndexes", entry.getValue());
                conflicts.add(conflict);
            }
        }
        return Result.success(Map.of("hasConflicts", !conflicts.isEmpty(), "conflicts", conflicts));
    }

    private CustomerProductRule cloneRule(CustomerProductRule src, Long targetCustomerId) {
        CustomerProductRule copy = new CustomerProductRule();
        copy.setCustomerId(targetCustomerId);
        copy.setRuleType(src.getRuleType());
        copy.setMatchMode(src.getMatchMode());
        copy.setName(src.getName());
        copy.setPriority(src.getPriority());
        copy.setProductId(src.getProductId());
        copy.setVariantId(src.getVariantId());
        copy.setKeywords(src.getKeywords());
        copy.setExcludeKeywords(src.getExcludeKeywords());
        copy.setMaterials(src.getMaterials());
        copy.setTemperature(src.getTemperature());
        copy.setBagSizeEquals(src.getBagSizeEquals());
        copy.setMaxBagSizeExclusive(src.getMaxBagSizeExclusive());
        copy.setMinInstrumentCount(src.getMinInstrumentCount());
        copy.setMaxInstrumentCount(src.getMaxInstrumentCount());
        copy.setPrice(src.getPrice());
        copy.setAcceptedPrices(src.getAcceptedPrices());
        copy.setFee(src.getFee());
        copy.setMultiplier(src.getMultiplier());
        copy.setThreshold(src.getThreshold());
        copy.setFoldRatio(src.getFoldRatio());
        copy.setSkipPackaging(src.getSkipPackaging());
        copy.setSkipDiscount(src.getSkipDiscount());
        copy.setIsActive(src.getIsActive());
        return copy;
    }

    private int countRules(String rulesJson) {
        try {
            var node = MAPPER.readTree(rulesJson);
            return node.path("productRules").size();
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> template(String code, String name, String type, String desc) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("code", code);
        t.put("name", name);
        t.put("ruleType", type);
        t.put("description", desc);
        return t;
    }
}
