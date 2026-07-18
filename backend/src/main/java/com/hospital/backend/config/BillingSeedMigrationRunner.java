package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 幂等加载 billing-seeds/*.json 客户策略/规则/客户组配置。
 */
@Slf4j
@Component
@Order(115)
@RequiredArgsConstructor
public class BillingSeedMigrationRunner implements CommandLineRunner {

    private static final String MARKER = "billing_seed_profiles_v1";
    private static final List<String> SEED_FILES = List.of(
            "billing-seeds/phase1-batch-a-extra.json",
            "billing-seeds/phase2-policies.json",
            "billing-seeds/phase5-batch-c.json",
            "billing-seeds/phase7-batch-d.json",
            "billing-seeds/phase7-batch-e.json",
            "billing-seeds/phase-missing-bokang-ref.json"
    );

    private final SysSettingMapper sysSettingMapper;
    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;
    private final CustomerDiscountMapper customerDiscountMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;
    private final CustomerBillingPolicyMapper billingPolicyMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final CustomerGroupMemberMapper customerGroupMemberMapper;

    @Override
    public void run(String... args) {
        if (sysSettingMapper.countByKey(MARKER) > 0) {
            return;
        }
        for (String file : SEED_FILES) {
            try {
                ClassPathResource resource = new ClassPathResource(file);
                if (!resource.exists()) {
                    log.warn("Billing seed file missing: {}", file);
                    continue;
                }
                JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
                seedProfiles(root.path("profiles"));
                seedCustomerGroups(root.path("customerGroups"));
                log.info("Loaded billing seed: {}", file);
            } catch (Exception e) {
                log.error("Failed to load billing seed {}: {}", file, e.getMessage(), e);
            }
        }
        SysSetting marker = new SysSetting();
        marker.setSettingKey(MARKER);
        marker.setSettingValue("true");
        marker.setDescription("BillingSeedMigrationRunner v1 完成标记");
        sysSettingMapper.insert(marker);
        log.info("Billing seed migration complete.");
    }

    private void seedProfiles(JsonNode profiles) {
        if (!profiles.isArray()) {
            return;
        }
        for (JsonNode profile : profiles) {
            String code = text(profile, "code");
            if (code == null) {
                continue;
            }
            Customer customer = ensureCustomer(profile);
            applyCustomerFields(customer, profile);
            seedAliases(customer.getId(), profile.path("aliases"));
            seedDiscounts(customer.getId(), profile.path("discounts"));
            seedPolicies(customer.getId(), profile.path("policies"));
            seedProductRules(customer.getId(), profile.path("productRules"));
        }
    }

    private Customer ensureCustomer(JsonNode profile) {
        String code = text(profile, "code");
        Customer existing = customerMapper.selectByCode(code);
        if (existing != null) {
            return existing;
        }
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(text(profile, "name"));
        customer.setStatus("active");
        customer.setBillingEnabled(bool(profile, "billingEnabled", false));
        customer.setBillingPricingMode(text(profile, "billingPricingMode", "standard"));
        customer.setNotes(text(profile, "notes"));
        if (profile.hasNonNull("exportNameMapping")) {
            customer.setExportNameMapping(profile.get("exportNameMapping").toString());
        }
        customerMapper.insert(customer);
        log.info("Seeded customer: {}", code);
        return customer;
    }

    private void applyCustomerFields(Customer customer, JsonNode profile) {
        boolean changed = false;
        if (profile.has("billingEnabled")) {
            Boolean enabled = bool(profile, "billingEnabled", false);
            if (!enabled.equals(customer.getBillingEnabled())) {
                customer.setBillingEnabled(enabled);
                changed = true;
            }
        }
        if (profile.has("billingPricingMode")) {
            String mode = text(profile, "billingPricingMode");
            if (mode != null && !mode.equals(customer.getBillingPricingMode())) {
                customer.setBillingPricingMode(mode);
                changed = true;
            }
        }
        if (profile.hasNonNull("exportNameMapping") && customer.getExportNameMapping() == null) {
            customer.setExportNameMapping(profile.get("exportNameMapping").toString());
            changed = true;
        }
        if (profile.hasNonNull("notes") && (customer.getNotes() == null || customer.getNotes().isBlank())) {
            customer.setNotes(text(profile, "notes"));
            changed = true;
        }
        if (changed) {
            customerMapper.updateById(customer);
        }
    }

    private void seedAliases(Long customerId, JsonNode aliases) {
        if (!aliases.isArray()) {
            return;
        }
        List<String> existingAliases = customerAliasMapper.selectByCustomerId(customerId).stream()
                .map(CustomerAlias::getAlias)
                .toList();
        for (JsonNode aliasNode : aliases) {
            String alias = aliasNode.asText();
            if (alias == null || alias.isBlank() || existingAliases.contains(alias)) {
                continue;
            }
            CustomerAlias entity = new CustomerAlias();
            entity.setCustomerId(customerId);
            entity.setAlias(alias);
            entity.setMatchType("contains");
            entity.setSource("seed");
            entity.setPriority(100);
            entity.setIsActive(true);
            customerAliasMapper.insert(entity);
        }
    }

    private void seedDiscounts(Long customerId, JsonNode discounts) {
        if (!discounts.isArray()) {
            return;
        }
        for (JsonNode discountNode : discounts) {
            String name = text(discountNode, "name");
            if (name == null || hasDiscountNamed(customerId, name)) {
                continue;
            }
            CustomerDiscount discount = new CustomerDiscount();
            discount.setCustomerId(customerId);
            discount.setName(name);
            discount.setDiscountRate(decimal(discountNode, "rate"));
            discount.setApplyStage("after_base");
            discount.setSkipWhenFixedPrice(true);
            discount.setPriority(intVal(discountNode, "priority", 100));
            discount.setIsActive(true);
            customerDiscountMapper.insert(discount);
        }
    }

    private void seedPolicies(Long customerId, JsonNode policies) {
        if (!policies.isArray()) {
            return;
        }
        for (JsonNode policyNode : policies) {
            String name = text(policyNode, "name");
            String type = text(policyNode, "policyType");
            if (name == null || type == null) {
                continue;
            }
            List<CustomerBillingPolicy> existing = billingPolicyMapper.selectByCustomerIdAndType(customerId, type);
            if (existing != null && existing.stream().anyMatch(p -> name.equals(p.getName()))) {
                continue;
            }
            CustomerBillingPolicy policy = new CustomerBillingPolicy();
            policy.setCustomerId(customerId);
            policy.setPolicyType(type);
            policy.setName(name);
            if (policyNode.has("scope")) {
                policy.setScope(policyNode.get("scope").toString());
            }
            if (policyNode.has("params")) {
                policy.setParams(policyNode.get("params").toString());
            }
            policy.setPriority(intVal(policyNode, "priority", 100));
            policy.setIsActive(true);
            billingPolicyMapper.insert(policy);
        }
    }

    private void seedProductRules(Long customerId, JsonNode rules) {
        if (!rules.isArray()) {
            return;
        }
        for (JsonNode ruleNode : rules) {
            String name = text(ruleNode, "name");
            if (name == null || customerProductRuleMapper.countByCustomerIdAndName(customerId, name) > 0) {
                continue;
            }
            CustomerProductRule rule = new CustomerProductRule();
            rule.setCustomerId(customerId);
            rule.setRuleType(text(ruleNode, "ruleType", "FIXED_PRICE"));
            rule.setName(name);
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
            rule.setSkipPackaging(bool(ruleNode, "skipPackaging", false));
            rule.setSkipDiscount(bool(ruleNode, "skipDiscount", false));
            rule.setMatchMode(text(ruleNode, "matchMode", "first"));
            if (ruleNode.has("acceptedPrices")) {
                rule.setAcceptedPrices(ruleNode.get("acceptedPrices").toString());
            }
            rule.setIsActive(true);
            customerProductRuleMapper.insert(rule);
        }
    }

    private void seedCustomerGroups(JsonNode groups) {
        if (!groups.isArray()) {
            return;
        }
        for (JsonNode groupNode : groups) {
            String name = text(groupNode, "name");
            if (name == null) {
                continue;
            }
            CustomerGroup group = findGroupByName(name);
            if (group == null) {
                group = new CustomerGroup();
                group.setName(name);
                group.setGroupType(text(groupNode, "groupType", "settlement_merge"));
                if (groupNode.has("config")) {
                    group.setConfig(groupNode.get("config").toString());
                }
                group.setIsActive(true);
                customerGroupMapper.insert(group);
            }
            seedGroupMembers(group.getId(), groupNode.path("memberCodes"));
        }
    }

    private CustomerGroup findGroupByName(String name) {
        List<CustomerGroup> all = customerGroupMapper.selectAll(null);
        if (all == null) {
            return null;
        }
        return all.stream().filter(g -> name.equals(g.getName())).findFirst().orElse(null);
    }

    private void seedGroupMembers(Long groupId, JsonNode memberCodes) {
        if (!memberCodes.isArray()) {
            return;
        }
        for (JsonNode codeNode : memberCodes) {
            String code = codeNode.asText();
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                log.warn("Customer group member code not found: {}", code);
                continue;
            }
            if (customerGroupMemberMapper.selectByGroupAndCustomer(groupId, customer.getId()) != null) {
                continue;
            }
            CustomerGroupMember member = new CustomerGroupMember();
            member.setGroupId(groupId);
            member.setCustomerId(customer.getId());
            member.setShareRatio(1.0);
            customerGroupMemberMapper.insert(member);
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

    private static int intVal(JsonNode node, String field, Integer defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue != null ? defaultValue : 0;
        }
        return node.get(field).asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        return new BigDecimal(node.get(field).asText());
    }

    private boolean hasDiscountNamed(Long customerId, String name) {
        List<CustomerDiscount> discounts = customerDiscountMapper.selectByCustomerId(customerId);
        if (discounts == null) {
            return false;
        }
        return discounts.stream().anyMatch(d -> name.equals(d.getName()));
    }
}
