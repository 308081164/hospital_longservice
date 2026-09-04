package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.SysSetting;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.SysSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 启动时按 classpath billing-rules-manifest.json 全量 upsert productRules（幂等）。
 */
@Slf4j
@Component
@Order(116)
@RequiredArgsConstructor
public class BillingRulesManifestReconciler implements CommandLineRunner {

    private static final String MANIFEST_FILE = "billing-seeds/billing-rules-manifest.json";
    private static final String MANIFEST_HASH_KEY = "billing_rules_manifest_hash";
    private static final String MANIFEST_GENERATED_AT_KEY = "billing_rules_manifest_generated_at";
    private static final String MANIFEST_RECONCILED_AT_KEY = "billing_rules_manifest_reconciled_at";

    private final CustomerMapper customerMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;
    private final SysSettingMapper sysSettingMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${billing.seed.reconcile-enabled:true}")
    private boolean reconcileEnabled;

    @Override
    public void run(String... args) {
        if (!reconcileEnabled) {
            log.info("Billing rules manifest reconcile disabled (billing.seed.reconcile-enabled=false)");
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource(MANIFEST_FILE);
            if (!resource.exists()) {
                log.warn("Billing rules manifest missing: {}", MANIFEST_FILE);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            String manifestHash = text(root, "manifest_hash");
            if (manifestHash == null || manifestHash.isBlank()) {
                log.warn("Billing rules manifest has no manifest_hash, skipped");
                return;
            }
            // 始终全量 reconcile（幂等）：hash 相同也继续执行。
            // 历史教训（2026-09-02 平房区人民 0.5/针事故）：生产通过 UI/手工 SQL 加入的非 manifest 规则，
            // 在 hash 未变的多次部署中被 skip 逻辑跳过而长期残留并参与计价；只有每次启动都清理才能杜绝漂移。
            SysSetting existing = sysSettingMapper.selectByKey(MANIFEST_HASH_KEY);
            if (existing != null && manifestHash.equals(existing.getSettingValue())) {
                log.info("Billing rules manifest unchanged (hash={}…), reconcile still runs to clean drift",
                        manifestHash.substring(0, Math.min(12, manifestHash.length())));
            }
            JsonNode customers = root.path("customers");
            if (!customers.isObject()) {
                log.warn("Billing rules manifest customers node invalid");
                return;
            }
            int upserted = 0;
            int customersUpdated = 0;
            Map<Long, Set<String>> manifestRules = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = customers.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String code = entry.getKey();
                JsonNode customerNode = entry.getValue();
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.debug("Reconcile skipped unknown customer: {}", code);
                    continue;
                }
                if (applyCustomerManifestFields(customer, customerNode)) {
                    customersUpdated++;
                    customer = customerMapper.selectByCode(code);
                }
                if (isInactiveCustomer(customer)) {
                    log.info("Reconcile skipped inactive customer rules: {}", code);
                    continue;
                }
                JsonNode rules = customerNode.path("productRules");
                if (rules.isArray()) {
                    Set<String> names = new HashSet<>();
                    for (JsonNode ruleNode : rules) {
                        upsertProductRule(customer.getId(), ruleNode);
                        String ruleName = text(ruleNode, "name");
                        if (ruleName != null && !ruleName.isBlank()) {
                            names.add(ruleName);
                        }
                        upserted++;
                    }
                    manifestRules.put(customer.getId(), names);
                }
            }
            int deleted = syncDeleteNonManifestRules(manifestRules);
            if (deleted > 0) {
                log.info("Reconcile cleaned up {} non-manifest rules", deleted);
            }
            upsertManifestHash(manifestHash);
            upsertSetting(MANIFEST_GENERATED_AT_KEY, text(root, "generated_at"),
                    "ISO timestamp of billing-rules-manifest.json generation");
            upsertSetting(MANIFEST_RECONCILED_AT_KEY, java.time.Instant.now().toString(),
                    "ISO timestamp of last billing-rules-manifest reconcile on this instance");
            log.info("Billing rules manifest reconcile done: {} rules upserted, {} customers updated, hash={}…",
                    upserted, customersUpdated, manifestHash.substring(0, Math.min(12, manifestHash.length())));
        } catch (Exception e) {
            log.error("Billing rules manifest reconcile failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 让 DB 的 customer_product_rule 与 manifest 完全一致：
     * 1) 非 special-pricing 客户（billing_enabled=0 或 standard 模式）删除全部规则；
     * 2) manifest 内的客户删除不在 manifest 里的规则名。
     */
    private int syncDeleteNonManifestRules(Map<Long, Set<String>> manifestRules) {
        int deleted = 0;
        // 1) 非特殊计价客户清空规则
        deleted += jdbcTemplate.update(
                "DELETE r FROM customer_product_rule r "
                        + "JOIN customer c ON c.id = r.customer_id "
                        + "WHERE c.billing_enabled = 0 OR c.billing_pricing_mode = 'standard'");
        // 2) manifest 客户按规则名清理多余规则
        for (Map.Entry<Long, Set<String>> entry : manifestRules.entrySet()) {
            List<CustomerProductRule> existing = customerProductRuleMapper.selectByCustomerId(entry.getKey());
            for (CustomerProductRule rule : existing) {
                if (!entry.getValue().contains(rule.getName())) {
                    customerProductRuleMapper.deleteById(rule.getId());
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private boolean applyCustomerManifestFields(Customer customer, JsonNode node) {
        boolean changed = false;
        if (node.hasNonNull("billingPricingMode")) {
            String mode = text(node, "billingPricingMode");
            if (mode != null && !mode.equals(customer.getBillingPricingMode())) {
                customer.setBillingPricingMode(mode);
                changed = true;
            }
        }
        if (node.has("standardPricingOverride") && !node.get("standardPricingOverride").isNull()) {
            String override = node.get("standardPricingOverride").toString();
            if (!override.equals(customer.getStandardPricingOverride())) {
                customer.setStandardPricingOverride(override);
                changed = true;
            }
        }
        if (node.has("billingEnabled") && !node.get("billingEnabled").isNull()) {
            boolean enabled = node.get("billingEnabled").asBoolean();
            if (!Objects.equals(enabled, Boolean.TRUE.equals(customer.getBillingEnabled()))) {
                customer.setBillingEnabled(enabled);
                changed = true;
            }
        }
        if (node.hasNonNull("status")) {
            String status = text(node, "status");
            if (status != null && !status.equals(customer.getStatus())) {
                customer.setStatus(status);
                changed = true;
            }
        }
        if (changed) {
            customerMapper.updateById(customer);
            log.info("Reconcile updated customer {} billing fields", customer.getCode());
        }
        return changed;
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
        if (ruleNode.hasNonNull("billingMode")) {
            rule.setBillingMode(text(ruleNode, "billingMode"));
        }
        if (ruleNode.hasNonNull("pieceCountSource")) {
            rule.setPieceCountSource(text(ruleNode, "pieceCountSource"));
        }
        rule.setPriority(intVal(ruleNode, "priority", 100));
        if (ruleNode.hasNonNull("price")) {
            rule.setPrice(decimal(ruleNode, "price"));
        }
        if (ruleNode.hasNonNull("fee")) {
            rule.setFee(decimal(ruleNode, "fee"));
        }
        if (ruleNode.has("materials")) {
            rule.setMaterials(toJsonArray(ruleNode.get("materials")));
        }
        if (ruleNode.hasNonNull("foldRatio")) {
            rule.setFoldRatio(decimal(ruleNode, "foldRatio"));
        }
        if (ruleNode.hasNonNull("threshold")) {
            rule.setThreshold(intVal(ruleNode, "threshold", null));
        }
        if (ruleNode.has("extraCount")) {
            if (ruleNode.get("extraCount").isNull()) {
                rule.setExtraCount(null);
            } else {
                rule.setExtraCount(intVal(ruleNode, "extraCount", null));
            }
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
        if (ruleNode.hasNonNull("minBagSizeInclusive")) {
            rule.setMinBagSizeInclusive(intVal(ruleNode, "minBagSizeInclusive", null));
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
        if (ruleNode.hasNonNull("keywordMatchMode")) {
            rule.setKeywordMatchMode(text(ruleNode, "keywordMatchMode"));
        }
        if (ruleNode.has("acceptedPrices")) {
            rule.setAcceptedPrices(ruleNode.get("acceptedPrices").toString());
        }
        if (ruleNode.hasNonNull("conditionsJson")) {
            rule.setConditionsJson(ruleNode.get("conditionsJson").asText());
        }
        if (ruleNode.has("isActive")) {
            rule.setIsActive(bool(ruleNode, "isActive", true));
        } else if (insert) {
            rule.setIsActive(true);
        }
        if (insert) {
            customerProductRuleMapper.insert(rule);
        } else {
            customerProductRuleMapper.updateById(rule);
        }
    }

    private CustomerProductRule findProductRuleByName(Long customerId, String ruleName) {
        return customerProductRuleMapper.selectByCustomerId(customerId).stream()
                .filter(r -> ruleName.equals(r.getName()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isInactiveCustomer(Customer customer) {
        String status = customer.getStatus();
        return status != null && "inactive".equalsIgnoreCase(status.trim());
    }

    private void upsertManifestHash(String hash) {
        upsertSetting(MANIFEST_HASH_KEY, hash, "SHA256 of billing-rules-manifest.json customers payload");
    }

    private void upsertSetting(String key, String value, String description) {
        if (value == null || value.isBlank()) {
            return;
        }
        SysSetting existing = sysSettingMapper.selectByKey(key);
        if (existing == null) {
            SysSetting marker = new SysSetting();
            marker.setSettingKey(key);
            marker.setSettingValue(value);
            marker.setDescription(description);
            sysSettingMapper.insert(marker);
        } else {
            existing.setSettingValue(value);
            if (description != null && !description.isBlank()) {
                existing.setDescription(description);
            }
            sysSettingMapper.updateByKey(existing);
        }
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

    private static String text(JsonNode node, String field) {
        return text(node, field, null);
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
