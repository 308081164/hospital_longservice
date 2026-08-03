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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    private final CustomerMapper customerMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;
    private final SysSettingMapper sysSettingMapper;

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
            SysSetting existing = sysSettingMapper.selectByKey(MANIFEST_HASH_KEY);
            if (existing != null && manifestHash.equals(existing.getSettingValue())) {
                log.info("Billing rules manifest unchanged (hash={}…), reconcile skipped",
                        manifestHash.substring(0, Math.min(12, manifestHash.length())));
                return;
            }
            JsonNode customers = root.path("customers");
            if (!customers.isObject()) {
                log.warn("Billing rules manifest customers node invalid");
                return;
            }
            int upserted = 0;
            int customersUpdated = 0;
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
                }
                JsonNode rules = customerNode.path("productRules");
                if (rules.isArray()) {
                    for (JsonNode ruleNode : rules) {
                        upsertProductRule(customer.getId(), ruleNode);
                        upserted++;
                    }
                }
            }
            upsertManifestHash(manifestHash);
            log.info("Billing rules manifest reconcile done: {} rules upserted, {} customers updated, hash={}…",
                    upserted, customersUpdated, manifestHash.substring(0, Math.min(12, manifestHash.length())));
        } catch (Exception e) {
            log.error("Billing rules manifest reconcile failed: {}", e.getMessage(), e);
        }
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
        if (ruleNode.hasNonNull("foldRatio")) {
            rule.setFoldRatio(decimal(ruleNode, "foldRatio"));
        }
        if (ruleNode.hasNonNull("threshold")) {
            rule.setThreshold(intVal(ruleNode, "threshold", null));
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
        if (ruleNode.hasNonNull("minInstrumentCount")) {
            rule.setMinInstrumentCount(intVal(ruleNode, "minInstrumentCount", null));
        }
        if (ruleNode.hasNonNull("maxInstrumentCount")) {
            rule.setMaxInstrumentCount(intVal(ruleNode, "maxInstrumentCount", null));
        }
        rule.setSkipPackaging(bool(ruleNode, "skipPackaging", false));
        rule.setSkipDiscount(bool(ruleNode, "skipDiscount", false));
        rule.setMatchMode(text(ruleNode, "matchMode", "first"));
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

    private void upsertManifestHash(String hash) {
        SysSetting existing = sysSettingMapper.selectByKey(MANIFEST_HASH_KEY);
        if (existing == null) {
            SysSetting marker = new SysSetting();
            marker.setSettingKey(MANIFEST_HASH_KEY);
            marker.setSettingValue(hash);
            marker.setDescription("SHA256 of billing-rules-manifest.json customers payload");
            sysSettingMapper.insert(marker);
        } else {
            existing.setSettingValue(hash);
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
