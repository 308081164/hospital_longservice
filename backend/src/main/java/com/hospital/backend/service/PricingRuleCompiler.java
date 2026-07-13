package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerBillingPolicy;
import com.hospital.backend.entity.CustomerDiscount;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.Product;
import com.hospital.backend.entity.ProductMatchRule;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerDiscountMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 将 hospital_pricing_rule 基础 JSON 与客户级 DB 规则（customer_product_rule / customer_discount）编译为
 * PricingEngine 可消费的 enriched rules_json。
 * <p>客户商品规则（specialRules 各子数组）优先于通用 rules_json 中的同名规则；客户规则内部仍按 DB 的 priority ASC, id ASC。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRuleCompiler {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final CustomerResolver customerResolver;
    private final CustomerProductRuleMapper productRuleMapper;
    private final CustomerBillingPolicyMapper billingPolicyMapper;
    private final CustomerDiscountMapper discountMapper;
    private final ProductMapper productMapper;
    private final ProductMatchRuleMapper productMatchRuleMapper;
    private final RuleSchemaValidator ruleSchemaValidator;

    public JsonNode compile(JsonNode baseRules, String hospitalName) {
        warnIfInvalid(baseRules);
        ObjectNode compiled = baseRules.deepCopy();
        Optional<Customer> customerOpt = customerResolver.resolveByName(hospitalName);
        if (customerOpt.isEmpty()) {
            return compiled;
        }

        Customer customer = customerOpt.get();
        List<String> hospitalNames = customerResolver.hospitalNamesForCustomer(customer);

        ObjectNode billingProfile = MAPPER.createObjectNode();
        billingProfile.put("enabled", Boolean.TRUE.equals(customer.getBillingEnabled()));
        String pricingMode = customer.getBillingPricingMode();
        if (pricingMode != null && !pricingMode.isBlank()) {
            billingProfile.put("pricingMode", pricingMode.trim().toLowerCase());
        } else {
            billingProfile.put("pricingMode", "standard");
        }
        appendPathOverride(billingProfile, customer.getPathOverride());
        compiled.set("billingProfile", billingProfile);

        ObjectNode specialRules = ensureObject(compiled, "specialRules");
        if (Boolean.TRUE.equals(customer.getBillingEnabled())) {
            mergeCustomerProductRules(specialRules, customer, hospitalNames);
        }
        applyBillingPolicies(compiled, customer);
        applyCustomerOverrides(compiled, customer, hospitalName);
        return compiled;
    }

    /**
     * 将客户商品规则 prepend 到 specialRules 各子数组前端，保证引擎 first-match 时客户特色优先生效。
     * 客户规则内部顺序保持 DB 查询序（priority ASC, id ASC），不改变；仅调整与通用规则的相对位置。
     */
    private void mergeCustomerProductRules(ObjectNode specialRules, Customer customer, List<String> hospitalNames) {
        List<CustomerProductRule> rules = productRuleMapper.selectByCustomerId(customer.getId());
        if (rules.isEmpty()) {
            return;
        }

        ArrayNode customerFixedPrices = MAPPER.createArrayNode();
        ArrayNode customerFoldRules = MAPPER.createArrayNode();
        ArrayNode customerExtraFees = MAPPER.createArrayNode();
        ArrayNode customerPriceMultipliers = MAPPER.createArrayNode();

        for (CustomerProductRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getIsActive())) {
                continue;
            }
            String ruleType = rule.getRuleType() != null ? rule.getRuleType() : "";
            switch (ruleType) {
                case "FIXED_PRICE", "PRICE_PER_INSTRUMENT" ->
                        customerFixedPrices.add(toFixedPriceNode(rule, hospitalNames));
                case "MULTIPLIER" -> customerPriceMultipliers.add(toMultiplierNode(rule, hospitalNames));
                case "FOLD" -> customerFoldRules.add(toFoldRuleNode(rule, hospitalNames));
                case "EXTRA_FEE", "ADD_FEE" -> customerExtraFees.add(toExtraFeeNode(rule, hospitalNames));
                default -> { }
            }
        }

        prependCustomerRules(specialRules, "fixedPrices", customerFixedPrices);
        prependCustomerRules(specialRules, "foldRules", customerFoldRules);
        prependCustomerRules(specialRules, "extraFees", customerExtraFees);
        prependCustomerRules(specialRules, "priceMultipliers", customerPriceMultipliers);
    }

    /** 客户规则在前、通用规则在后，写入 specialRules 对应子数组。 */
    private void prependCustomerRules(ObjectNode specialRules, String field, ArrayNode customerRules) {
        if (customerRules.isEmpty()) {
            return;
        }
        ArrayNode existing = ensureArray(specialRules, field);
        ArrayNode merged = MAPPER.createArrayNode();
        merged.addAll(customerRules);
        merged.addAll(existing);
        specialRules.set(field, merged);
    }

    private void applyBillingPolicies(ObjectNode compiled, Customer customer) {
        List<CustomerBillingPolicy> policies = billingPolicyMapper.selectByCustomerId(customer.getId());
        if (policies.isEmpty()) {
            policies = synthesizePoliciesFromDiscounts(customer.getId());
        }
        ArrayNode billingPolicies = MAPPER.createArrayNode();
        for (CustomerBillingPolicy policy : policies) {
            if (!Boolean.TRUE.equals(policy.getIsActive())) {
                continue;
            }
            billingPolicies.add(toBillingPolicyNode(policy));
        }
        if (!billingPolicies.isEmpty()) {
            compiled.set("billingPolicies", billingPolicies);
        }
    }

    private List<CustomerBillingPolicy> synthesizePoliciesFromDiscounts(Long customerId) {
        List<CustomerDiscount> discounts = discountMapper.selectByCustomerId(customerId);
        List<CustomerBillingPolicy> synthesized = new ArrayList<>();
        for (CustomerDiscount discount : discounts) {
            if (!Boolean.TRUE.equals(discount.getIsActive()) || discount.getDiscountRate() == null) {
                continue;
            }
            CustomerBillingPolicy policy = new CustomerBillingPolicy();
            policy.setId(discount.getId());
            policy.setCustomerId(customerId);
            policy.setPolicyType("DISCOUNT");
            policy.setName(discount.getName());
            policy.setScope("{\"temperature\":\"ANY\"}");
            policy.setParams(String.format(
                    "{\"rate\":%s,\"skipWhenFixedPrice\":%s}",
                    discount.getDiscountRate(),
                    Boolean.TRUE.equals(discount.getSkipWhenFixedPrice())));
            policy.setPriority(discount.getPriority());
            policy.setIsActive(true);
            synthesized.add(policy);
        }
        return synthesized;
    }

    private ObjectNode toBillingPolicyNode(CustomerBillingPolicy policy) {
        ObjectNode node = MAPPER.createObjectNode();
        if (policy.getId() != null) {
            node.put("policyId", policy.getId());
        }
        node.put("policyType", policy.getPolicyType());
        if (policy.getName() != null) {
            node.put("name", policy.getName());
        }
        if (policy.getPriority() != null) {
            node.put("priority", policy.getPriority());
        }
        if (policy.getScope() != null && !policy.getScope().isBlank()) {
            try {
                node.set("scope", MAPPER.readTree(policy.getScope()));
            } catch (Exception ignored) {
                node.putObject("scope");
            }
        }
        if (policy.getParams() != null && !policy.getParams().isBlank()) {
            try {
                node.set("params", MAPPER.readTree(policy.getParams()));
            } catch (Exception ignored) {
                node.putObject("params");
            }
        }
        return node;
    }

    private void appendPathOverride(ObjectNode billingProfile, String pathOverrideJson) {
        if (pathOverrideJson == null || pathOverrideJson.isBlank()) {
            return;
        }
        try {
            billingProfile.set("pathOverride", MAPPER.readTree(pathOverrideJson));
        } catch (Exception ignored) {
            billingProfile.putObject("pathOverride");
        }
    }

    private void applyLogisticsOverride(ObjectNode overrides, JsonNode billingPolicies) {
        if (!billingPolicies.isArray()) {
            return;
        }
        for (JsonNode policy : billingPolicies) {
            if (!"LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            JsonNode feeNode = policy.path("params").path("feePerTrip");
            if (feeNode.isMissingNode() || feeNode.isNull()) {
                continue;
            }
            overrides.put("logisticsFeePerTrip", feeNode.asDouble());
            if (policy.has("policyId")) {
                overrides.put("logisticsPolicyId", policy.path("policyId").asLong());
            }
            if (policy.has("name")) {
                overrides.put("logisticsPolicyName", policy.path("name").asText());
            }
            break;
        }
    }

    private void applyCustomerOverrides(ObjectNode compiled, Customer customer, String hospitalName) {
        ObjectNode overrides = MAPPER.createObjectNode();
        overrides.put("displayName", hospitalName);

        JsonNode billingPolicies = compiled.path("billingPolicies");
        if (billingPolicies.isArray()) {
            for (JsonNode policy : billingPolicies) {
                if (!"DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                    continue;
                }
                String temperature = policy.path("scope").path("temperature").asText("ANY");
                if (!"ANY".equalsIgnoreCase(temperature)) {
                    continue;
                }
                double rate = policy.path("params").path("rate").asDouble(Double.NaN);
                if (Double.isNaN(rate)) {
                    continue;
                }
                overrides.put("discountRate", rate);
                overrides.put("skipWhenFixedPrice", policy.path("params").path("skipWhenFixedPrice").asBoolean(true));
                overrides.put("discountLabel", policy.path("name").asText("客户折扣"));
                break;
            }
        }

        if (!overrides.has("discountRate")) {
            List<CustomerDiscount> discounts = discountMapper.selectByCustomerId(customer.getId());
            for (CustomerDiscount discount : discounts) {
                if (!Boolean.TRUE.equals(discount.getIsActive())) {
                    continue;
                }
                if (discount.getDiscountRate() != null) {
                    overrides.put("discountRate", discount.getDiscountRate().doubleValue());
                    overrides.put("skipWhenFixedPrice", Boolean.TRUE.equals(discount.getSkipWhenFixedPrice()));
                    overrides.put("discountLabel", discount.getName());
                    break;
                }
            }
        }

        applyLogisticsOverride(overrides, billingPolicies);

        if (customer.getCapMode() != null && !customer.getCapMode().isBlank()) {
            ObjectNode htPaper = ensureObjectPath(compiled, "highTemperature", "paperPlastic");
            htPaper.put("capMode", customer.getCapMode());
        }
        if (Boolean.TRUE.equals(customer.getChargeDoubleBagWhenCapped())) {
            ObjectNode htPaper = ensureObjectPath(compiled, "highTemperature", "paperPlastic");
            htPaper.put("chargeDoubleBagWhenCapped", true);
        }

        compiled.set("customerOverrides", overrides);
    }

    private ObjectNode toFixedPriceNode(CustomerProductRule rule, List<String> hospitalNames) {
        ObjectNode node = MAPPER.createObjectNode();
        if (rule.getId() != null) {
            node.put("ruleId", rule.getId());
        }
        node.put("name", rule.getName());
        node.set("hospitals", MAPPER.valueToTree(hospitalNames));
        appendProductBinding(node, rule);
        appendJsonArray(node, "keywords", rule.getKeywords());
        appendJsonArray(node, "excludeKeywords", rule.getExcludeKeywords());
        appendJsonArray(node, "materials", rule.getMaterials());
        if (rule.getTemperature() != null && !rule.getTemperature().isBlank()) {
            node.put("temperature", rule.getTemperature().trim().toUpperCase());
        }
        String matchMode = rule.getMatchMode() != null ? rule.getMatchMode() : "first";
        node.put("matchMode", matchMode);
        appendAcceptedPrices(node, rule);
        if (rule.getBagSizeEquals() != null) {
            node.put("bagSizeEquals", rule.getBagSizeEquals());
        }
        if (rule.getMaxBagSizeExclusive() != null) {
            node.put("maxBagSizeExclusive", rule.getMaxBagSizeExclusive());
        }
        if (rule.getMinInstrumentCount() != null) {
            node.put("minInstrumentCount", rule.getMinInstrumentCount());
        }
        if (rule.getMaxInstrumentCount() != null) {
            node.put("maxInstrumentCount", rule.getMaxInstrumentCount());
        }
        if (rule.getPrice() != null) {
            node.put("price", rule.getPrice().doubleValue());
        }
        if ("PRICE_PER_INSTRUMENT".equals(rule.getRuleType())) {
            node.put("pricePerInstrument", true);
        }
        if (Boolean.TRUE.equals(rule.getSkipPackaging())) {
            node.put("skipPackaging", true);
        }
        if (Boolean.TRUE.equals(rule.getSkipDiscount())) {
            node.put("skipHospitalDiscount", true);
        }
        return node;
    }

    private void appendAcceptedPrices(ObjectNode node, CustomerProductRule rule) {
        if (rule.getAcceptedPrices() == null || rule.getAcceptedPrices().isBlank()) {
            return;
        }
        try {
            node.set("acceptedPrices", MAPPER.readTree(rule.getAcceptedPrices()));
        } catch (Exception ignored) {
            node.set("acceptedPrices", MAPPER.createArrayNode());
        }
    }

    private ObjectNode toMultiplierNode(CustomerProductRule rule, List<String> hospitalNames) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", rule.getName());
        node.set("hospitals", MAPPER.valueToTree(hospitalNames));
        appendProductBinding(node, rule);
        appendJsonArray(node, "keywords", rule.getKeywords());
        appendJsonArray(node, "materials", rule.getMaterials());
        if (rule.getBagSizeEquals() != null) {
            node.put("bagSizeEquals", rule.getBagSizeEquals());
        }
        if (rule.getMaxBagSizeExclusive() != null) {
            node.put("maxBagSizeExclusive", rule.getMaxBagSizeExclusive());
        }
        if (rule.getMinInstrumentCount() != null) {
            node.put("minInstrumentCount", rule.getMinInstrumentCount());
        }
        if (rule.getMaxInstrumentCount() != null) {
            node.put("maxInstrumentCount", rule.getMaxInstrumentCount());
        }
        if (rule.getMultiplier() != null) {
            node.put("multiplier", rule.getMultiplier().doubleValue());
        }
        if (Boolean.TRUE.equals(rule.getSkipDiscount())) {
            node.put("skipHospitalDiscount", true);
        }
        return node;
    }

    private void appendProductBinding(ObjectNode node, CustomerProductRule rule) {
        if (rule.getProductId() == null) {
            return;
        }
        node.put("productId", rule.getProductId());
        Product product = productMapper.selectById(rule.getProductId());
        if (product != null && product.getName() != null && !product.getName().isBlank()) {
            node.put("productName", product.getName());
        }
        List<String> derivedKeywords = deriveProductKeywords(rule);
        if (!derivedKeywords.isEmpty()) {
            ArrayNode keywords = MAPPER.createArrayNode();
            derivedKeywords.forEach(keywords::add);
            if (!node.has("keywords") || node.path("keywords").isEmpty()) {
                node.set("keywords", keywords);
            }
        }
    }

    private List<String> deriveProductKeywords(CustomerProductRule rule) {
        Set<String> keywords = new LinkedHashSet<>();
        if (rule.getProductId() == null) {
            return List.of();
        }
        Product product = productMapper.selectById(rule.getProductId());
        if (product != null && product.getName() != null && !product.getName().isBlank()) {
            keywords.add(product.getName().trim());
        }
        for (ProductMatchRule matchRule : productMatchRuleMapper.selectByProductId(rule.getProductId())) {
            if (!Boolean.TRUE.equals(matchRule.getIsActive())) {
                continue;
            }
            if ("contains".equalsIgnoreCase(matchRule.getMatchType())
                    && matchRule.getPatternValue() != null
                    && !matchRule.getPatternValue().isBlank()) {
                keywords.add(matchRule.getPatternValue().trim());
            }
        }
        return new ArrayList<>(keywords);
    }

    private ObjectNode toFoldRuleNode(CustomerProductRule rule, List<String> hospitalNames) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", rule.getName());
        node.set("hospitals", MAPPER.valueToTree(hospitalNames));
        appendJsonArray(node, "keywords", rule.getKeywords());
        if (rule.getMaxBagSizeExclusive() != null) {
            node.put("maxBagSizeExclusive", rule.getMaxBagSizeExclusive());
        }
        node.put("threshold", rule.getThreshold() != null ? rule.getThreshold() : 5);
        node.put("foldRatio", rule.getFoldRatio() != null ? rule.getFoldRatio().doubleValue() : 5.0);
        return node;
    }

    private ObjectNode toExtraFeeNode(CustomerProductRule rule, List<String> hospitalNames) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", rule.getName());
        node.set("hospitals", MAPPER.valueToTree(hospitalNames));
        appendJsonArray(node, "keywords", rule.getKeywords());
        if (rule.getFee() != null) {
            node.put("fee", rule.getFee().doubleValue());
        }
        return node;
    }

    private void appendJsonArray(ObjectNode node, String field, String json) {
        if (json == null || json.isBlank()) {
            node.set(field, MAPPER.createArrayNode());
            return;
        }
        try {
            node.set(field, MAPPER.readTree(json));
        } catch (Exception ignored) {
            node.set(field, MAPPER.createArrayNode());
        }
    }

    private ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = MAPPER.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private ArrayNode ensureArray(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = MAPPER.createArrayNode();
        parent.set(field, created);
        return created;
    }

    private ObjectNode ensureObjectPath(ObjectNode root, String... path) {
        ObjectNode current = root;
        for (String segment : path) {
            current = ensureObject(current, segment);
        }
        return current;
    }

    /** 供 Settings API 返回默认模板（含空 specialRules）。 */
    public Map<String, Object> defaultTemplateMap() {
        return new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
    }

    private void warnIfInvalid(JsonNode baseRules) {
        RuleSchemaValidator.ValidationResult result = ruleSchemaValidator.validateJsonNode(baseRules);
        if (!result.valid()) {
            log.warn("PricingRuleCompiler: 规则 JSON 校验未通过 — {}", result.message());
        }
    }
}
