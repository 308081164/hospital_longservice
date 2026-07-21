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
    /** 可在已有库上增量导入的单院种子（每项独立 marker，backend 重启时幂等执行一次） */
    private static final List<IncrementalSeed> INCREMENTAL_SEEDS = List.of(
            new IncrementalSeed("billing_seed_zyy_d1_v1", "billing-seeds/phase-zyy-d1-fuyi.json"),
            new IncrementalSeed("billing_seed_batch_p0_v1", "billing-seeds/phase-batch-p0.json"),
            new IncrementalSeed("billing_seed_batch_p0_1_v1", "billing-seeds/phase-batch-p0.1.json"),
            new IncrementalSeed("billing_seed_batch_p0_2_v1", "billing-seeds/phase-batch-p0.2.json"),
            new IncrementalSeed("billing_seed_batch_p0_3_v1", "billing-seeds/phase-batch-p0.3.json"),
            new IncrementalSeed("billing_seed_batch_p0_4_v1", "billing-seeds/phase-batch-p0.4.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_v1", "billing-seeds/phase-batch-p0.5.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_1_v1", "billing-seeds/phase-batch-p0.5.1.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_2_v1", "billing-seeds/phase-batch-p0.5.2.json"),
            new IncrementalSeed("billing_seed_batch_p0_6_v1", "billing-seeds/phase-batch-p0.6.json")
    );

    private static final String ZYY_D1_P0_MARKER = "billing_seed_zyy_d1_p0_v2";
    private static final String ZYY_D1_P0_1_MARKER = "billing_seed_zyy_d1_p0_1_v3";

    private record IncrementalSeed(String markerKey, String classpathFile) {}

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
        if (sysSettingMapper.countByKey(MARKER) == 0) {
            for (String file : SEED_FILES) {
                loadSeedClasspathFile(file);
            }
            insertMarker(MARKER, "BillingSeedMigrationRunner v1 完成标记");
            log.info("Billing seed migration complete.");
        }
        for (IncrementalSeed incremental : INCREMENTAL_SEEDS) {
            if (sysSettingMapper.countByKey(incremental.markerKey()) > 0) {
                continue;
            }
            if ("billing-seeds/phase-batch-p0.1.json".equals(incremental.classpathFile())) {
                applyBatchP0_1SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.4.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.json".equals(incremental.classpathFile())) {
                applyBatchP0_4SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.6.json".equals(incremental.classpathFile())) {
                applyBatchP0_6SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.3.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.1.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.2.json".equals(incremental.classpathFile())) {
                applyBatchPatchSeedFile(incremental.classpathFile());
            } else {
                loadSeedClasspathFile(incremental.classpathFile());
            }
            insertMarker(incremental.markerKey(), "Incremental billing seed: " + incremental.classpathFile());
            log.info("Incremental billing seed applied: {}", incremental.classpathFile());
        }
        if (sysSettingMapper.countByKey(ZYY_D1_P0_MARKER) == 0) {
            applyZyyD1P0RuleFixes();
            insertMarker(ZYY_D1_P0_MARKER, "ZYY-D1 P0 校对规则修正（停用宽泛无纺布、补精确产品规则）");
            log.info("ZYY-D1 P0 rule fixes applied.");
        }
        if (sysSettingMapper.countByKey(ZYY_D1_P0_1_MARKER) == 0) {
            applyZyyD1P0_1RuleFixes();
            insertMarker(ZYY_D1_P0_1_MARKER, "ZYY-D1 P0.1 收窄腔镜包/王树人/保温杯关键词");
            log.info("ZYY-D1 P0.1 rule fixes applied.");
        }
    }

    private void loadSeedClasspathFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Billing seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            seedProfiles(root.path("profiles"));
            seedCustomerGroups(root.path("customerGroups"));
            log.info("Loaded billing seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to load billing seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.1 补丁种子：更新 billing 模式 / 规则 keywords / 关闭零差异院 billing */
    private void applyBatchP0_1SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.1 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.1 customer update skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("billingEnabled")) {
                    customer.setBillingEnabled(bool(upd, "billingEnabled", false));
                }
                customerMapper.updateById(customer);
                log.info("P0.1 updated customer {}: mode={} enabled={}",
                        code, customer.getBillingPricingMode(), customer.getBillingEnabled());
            }
            for (JsonNode patch : root.path("ruleUpdates")) {
                String code = text(patch, "code");
                String ruleName = text(patch, "ruleName");
                if (code == null || ruleName == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                CustomerProductRule rule = findProductRuleByName(customer.getId(), ruleName);
                if (rule == null) {
                    log.warn("P0.1 rule patch skipped: {}/{}", code, ruleName);
                    continue;
                }
                List<String> keywords = parseStringList(rule.getKeywords());
                List<String> exclude = parseStringList(rule.getExcludeKeywords());
                for (JsonNode rm : patch.path("removeKeywords")) {
                    keywords.remove(rm.asText());
                }
                for (JsonNode add : patch.path("addKeywords")) {
                    String kw = add.asText();
                    if (!keywords.contains(kw)) {
                        keywords.add(kw);
                    }
                }
                for (JsonNode addEx : patch.path("addExcludeKeywords")) {
                    String ex = addEx.asText();
                    if (!exclude.contains(ex)) {
                        exclude.add(ex);
                    }
                }
                rule.setKeywords(JsonUtils.toJson(keywords));
                rule.setExcludeKeywords(exclude.isEmpty() ? null : JsonUtils.toJson(exclude));
                customerProductRuleMapper.updateById(rule);
                log.info("P0.1 patched rule {}/{} keywords={} exclude={}", code, ruleName, keywords, exclude);
            }
            for (JsonNode codeNode : root.path("deactivateBillingPolicies")) {
                String code = codeNode.asText();
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                billingPolicyMapper.selectByCustomerId(customer.getId()).forEach(p -> {
                    p.setIsActive(false);
                    billingPolicyMapper.updateById(p);
                });
                customerDiscountMapper.deleteByCustomerId(customer.getId());
                log.info("P0.1 deactivated policies/discounts for {}", code);
            }
            log.info("Applied P0.1 seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply P0.1 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.4：L9-L61 补充院 customer 收窄 + 工程大学口腔规则 */
    private void applyBatchP0_4SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.4 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.4 customer update skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("billingEnabled")) {
                    customer.setBillingEnabled(bool(upd, "billingEnabled", false));
                }
                customerMapper.updateById(customer);
                log.info("P0.4 updated customer {}: mode={} enabled={}",
                        code, customer.getBillingPricingMode(), customer.getBillingEnabled());
            }
            for (JsonNode codeNode : root.path("deactivateBillingPolicies")) {
                String code = codeNode.asText();
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                billingPolicyMapper.selectByCustomerId(customer.getId()).forEach(p -> {
                    p.setIsActive(false);
                    billingPolicyMapper.updateById(p);
                });
                customerDiscountMapper.deleteByCustomerId(customer.getId());
                log.info("P0.4 deactivated policies/discounts for {}", code);
            }
            for (JsonNode aliasNode : root.path("customerAliases")) {
                String code = text(aliasNode, "code");
                String alias = text(aliasNode, "alias");
                if (code == null || alias == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                ensureCustomerAliasExact(customer.getId(), alias,
                        text(aliasNode, "matchType", "exact"), "p0.4_seed", 10);
                log.info("P0.4 alias {} → {}", alias, code);
            }
            for (JsonNode ruleNode : root.path("newRules")) {
                String code = text(ruleNode, "code");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                String name = text(ruleNode, "name");
                if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
                    continue;
                }
                seedProductRules(customer.getId(), JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                log.info("P0.4 inserted rule {}/{}", code, name);
            }
            log.info("Applied P0.4 seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply P0.4 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.6：验收通过院启用 billing，其余停用 */
    private void applyBatchP0_6SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.6 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            List<String> enableCodes = new ArrayList<>();
            for (JsonNode codeNode : root.path("enableBilling")) {
                enableCodes.add(codeNode.asText());
            }
            if (enableCodes.isEmpty()) {
                log.warn("P0.6 enableBilling list is empty, skipped");
                return;
            }
            for (String code : enableCodes) {
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.6 enable skipped: {} not found", code);
                    continue;
                }
                customer.setBillingEnabled(true);
                customerMapper.updateById(customer);
                log.info("P0.6 enabled billing for {}", code);
            }
            if (bool(root, "disableAllOthers", false)) {
                List<Customer> all = customerMapper.selectAll();
                if (all != null) {
                    for (Customer customer : all) {
                        if (customer.getCode() == null || enableCodes.contains(customer.getCode())) {
                            continue;
                        }
                        if (!Boolean.TRUE.equals(customer.getBillingEnabled())) {
                            continue;
                        }
                        customer.setBillingEnabled(false);
                        customerMapper.updateById(customer);
                        log.info("P0.6 disabled billing for {}", customer.getCode());
                    }
                }
            }
            log.info("Applied P0.6 seed: {} ({} enabled)", file, enableCodes.size());
        } catch (Exception e) {
            log.error("Failed to apply P0.6 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.2+ 补丁种子：规则更新 / 新增 / 停用 */
    private void applyBatchPatchSeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Batch patch seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode patch : root.path("ruleUpdates")) {
                String code = text(patch, "code");
                String ruleName = text(patch, "ruleName");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                CustomerProductRule rule = findProductRuleByName(customer.getId(), ruleName);
                if (rule == null) {
                    log.warn("Batch patch rule skipped: {}/{}", code, ruleName);
                    continue;
                }
                boolean changed = false;
                if (patch.has("conditionsJson")) {
                    rule.setConditionsJson(patch.get("conditionsJson").asText());
                    changed = true;
                }
                if (patch.has("setKeywords")) {
                    rule.setKeywords(toJsonArray(patch.get("setKeywords")));
                    changed = true;
                } else {
                    List<String> keywords = parseStringList(rule.getKeywords());
                    List<String> exclude = parseStringList(rule.getExcludeKeywords());
                    for (JsonNode rm : patch.path("removeKeywords")) {
                        keywords.remove(rm.asText());
                    }
                    for (JsonNode add : patch.path("addKeywords")) {
                        String kw = add.asText();
                        if (!keywords.contains(kw)) {
                            keywords.add(kw);
                        }
                    }
                    for (JsonNode addEx : patch.path("addExcludeKeywords")) {
                        String ex = addEx.asText();
                        if (!exclude.contains(ex)) {
                            exclude.add(ex);
                        }
                    }
                    if (patch.has("removeKeywords") || patch.has("addKeywords")) {
                        rule.setKeywords(JsonUtils.toJson(keywords));
                        changed = true;
                    }
                    if (patch.has("addExcludeKeywords")) {
                        rule.setExcludeKeywords(JsonUtils.toJson(exclude));
                        changed = true;
                    }
                }
                if (patch.has("setMatchMode")) {
                    rule.setMatchMode(text(patch, "setMatchMode", "first"));
                    changed = true;
                }
                if (patch.has("setAcceptedPrices")) {
                    rule.setAcceptedPrices(patch.get("setAcceptedPrices").toString());
                    changed = true;
                }
                if (changed) {
                    customerProductRuleMapper.updateById(rule);
                    log.info("Batch patch updated rule {}/{}", code, ruleName);
                }
            }
            for (JsonNode ruleNode : root.path("newRules")) {
                String code = text(ruleNode, "code");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                String name = text(ruleNode, "name");
                if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
                    continue;
                }
                seedProductRules(customer.getId(), JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                log.info("Batch patch inserted rule {}/{}", code, name);
            }
            for (JsonNode deact : root.path("deactivateRules")) {
                String code = text(deact, "code");
                String ruleName = text(deact, "ruleName");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                deactivateProductRule(customer.getId(), ruleName);
            }
            log.info("Applied batch patch seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply batch patch seed {}: {}", file, e.getMessage(), e);
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            List<String> out = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    out.add(item.asText());
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void insertMarker(String key, String description) {
        SysSetting marker = new SysSetting();
        marker.setSettingKey(key);
        marker.setSettingValue("true");
        marker.setDescription(description);
        sysSettingMapper.insert(marker);
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

    private void ensureCustomerAliasExact(Long customerId, String alias, String matchType,
                                          String source, int priority) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        boolean exists = customerAliasMapper.selectByCustomerId(customerId).stream()
                .anyMatch(a -> alias.equals(a.getAlias()));
        if (exists) {
            return;
        }
        CustomerAlias entity = new CustomerAlias();
        entity.setCustomerId(customerId);
        entity.setAlias(alias);
        entity.setMatchType(matchType != null && !matchType.isBlank() ? matchType : "exact");
        entity.setSource(source);
        entity.setPriority(priority);
        entity.setIsActive(true);
        customerAliasMapper.insert(entity);
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

    /**
     * 附一 P0.1：收窄关键词，消除腹腔镜包/小王树人/非Z2044保温杯误报。
     */
    private void applyZyyD1P0_1RuleFixes() {
        Customer customer = customerMapper.selectByCode("ZYY-D1");
        if (customer == null) {
            log.warn("ZYY-D1 P0.1 fixes skipped: customer not found");
            return;
        }
        Long customerId = customer.getId();
        updateRuleKeywords(customerId, "王树人特器w12050", List.of("王树人特器-26"));
        updateRuleKeywords(customerId, "低温袋10cm", List.of("低温灭菌 10cm"));
        updateRuleKeywordsAndExclude(customerId, "腔镜包整包价",
                List.of("腔镜包"), List.of("腹腔镜"));
        try {
            ClassPathResource resource = new ClassPathResource("billing-seeds/phase-zyy-d1-fuyi.json");
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode profile : root.path("profiles")) {
                if (!"ZYY-D1".equals(text(profile, "code"))) {
                    continue;
                }
                for (JsonNode ruleNode : profile.path("productRules")) {
                    if ("保温杯-1Z2044".equals(text(ruleNode, "name"))) {
                        seedProductRules(customerId, JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                        break;
                    }
                }
                break;
            }
        } catch (Exception e) {
            log.error("ZYY-D1 P0.1 seed insert failed: {}", e.getMessage(), e);
        }
    }

    private void updateRuleKeywordsAndExclude(Long customerId, String ruleName,
                                              List<String> keywords, List<String> excludeKeywords) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null) {
            return;
        }
        rule.setKeywords(JsonUtils.toJson(keywords));
        rule.setExcludeKeywords(JsonUtils.toJson(excludeKeywords));
        customerProductRuleMapper.updateById(rule);
        log.info("Updated keywords/exclude for rule {} (customerId={})", ruleName, customerId);
    }

    /**
     * 附一 6 月校对 P0：停用误报规则、更新关键词、补精确产品固定价。
     */
    private void applyZyyD1P0RuleFixes() {
        Customer customer = customerMapper.selectByCode("ZYY-D1");
        if (customer == null) {
            log.warn("ZYY-D1 P0 fixes skipped: customer not found");
            return;
        }
        Long customerId = customer.getId();
        deactivateProductRule(customerId, "无纺布按把4.4");
        deactivateProductRule(customerId, "纸塑袋3件最低把价");
        updateRuleKeywords(customerId, "低温袋10cm", List.of("低温灭菌 10cm", "保温杯"));
        updateRuleKeywords(customerId, "低温袋15cm", List.of("低温灭菌 15cm", "膀胱取石钳"));
        try {
            ClassPathResource resource = new ClassPathResource("billing-seeds/phase-zyy-d1-fuyi.json");
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode profile : root.path("profiles")) {
                if (!"ZYY-D1".equals(text(profile, "code"))) {
                    continue;
                }
                seedProductRules(customerId, profile.path("productRules"));
                break;
            }
        } catch (Exception e) {
            log.error("ZYY-D1 P0 seedProductRules failed: {}", e.getMessage(), e);
        }
    }

    private void deactivateProductRule(Long customerId, String ruleName) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null || !Boolean.TRUE.equals(rule.getIsActive())) {
            return;
        }
        rule.setIsActive(false);
        customerProductRuleMapper.updateById(rule);
        log.info("Deactivated customer product rule: {} (customerId={})", ruleName, customerId);
    }

    private void updateRuleKeywords(Long customerId, String ruleName, List<String> keywords) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null) {
            return;
        }
        rule.setKeywords(JsonUtils.toJson(keywords));
        customerProductRuleMapper.updateById(rule);
        log.info("Updated keywords for rule {} (customerId={})", ruleName, customerId);
    }

    private CustomerProductRule findProductRuleByName(Long customerId, String ruleName) {
        return customerProductRuleMapper.selectByCustomerId(customerId).stream()
                .filter(r -> ruleName.equals(r.getName()))
                .findFirst()
                .orElse(null);
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
            if (ruleNode.has("conditionsJson")) {
                rule.setConditionsJson(ruleNode.get("conditionsJson").asText());
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
