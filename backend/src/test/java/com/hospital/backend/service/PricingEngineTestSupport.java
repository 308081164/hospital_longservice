package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerBillingRuleGroupMapper;
import com.hospital.backend.mapper.CustomerDiscountMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import com.hospital.backend.mapper.ProductVariantMapper;
import org.mockito.Mockito;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Compile manifest customer rules through {@link PricingRuleCompiler} for unit tests.
 */
public final class PricingEngineTestSupport {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();
    private static JsonNode manifestCache;
    private static JsonNode fixturesCache;
    private static final AtomicLong RULE_ID = new AtomicLong(10_000L);

    private PricingEngineTestSupport() {
    }

    public static JsonNode manifest() throws Exception {
        if (manifestCache == null) {
            try (InputStream in = PricingEngineTestSupport.class.getResourceAsStream("/billing-rules-manifest.json")) {
                if (in == null) {
                    throw new IllegalStateException("billing-rules-manifest.json missing from test resources");
                }
                manifestCache = MAPPER.readTree(in);
            }
        }
        return manifestCache;
    }

    public static JsonNode fixtures() throws Exception {
        if (fixturesCache == null) {
            try (InputStream in = PricingEngineTestSupport.class.getResourceAsStream("/pricing-engine/sc11-fixtures.json")) {
                if (in == null) {
                    throw new IllegalStateException("sc11-fixtures.json missing from test resources");
                }
                fixturesCache = MAPPER.readTree(in);
            }
        }
        return fixturesCache;
    }

    public static JsonNode registry() throws Exception {
        try (InputStream in = PricingEngineTestSupport.class.getResourceAsStream("/pricing-engine/rule-type-registry.json")) {
            if (in == null) {
                throw new IllegalStateException("rule-type-registry.json missing from test resources");
            }
            return MAPPER.readTree(in);
        }
    }

    public static JsonNode compileForCustomerCode(String customerCode) throws Exception {
        JsonNode customerNode = manifest().path("customers").path(customerCode);
        if (customerNode.isMissingNode()) {
            throw new IllegalArgumentException("Unknown customer code: " + customerCode);
        }
        Customer customer = toCustomer(customerNode);
        List<CustomerProductRule> rules = toProductRules(customerNode.path("productRules"), customer.getId());
        PricingRuleCompiler compiler = mockCompiler(customer, rules);
        JsonNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        return compiler.compileForCustomer(base, customer, customer.getCanonicalName());
    }

    public static PricingEngine engineForCustomerCode(String customerCode) throws Exception {
        return new PricingEngine(compileForCustomerCode(customerCode));
    }

    public static ObjectNode defaultRules() {
        return (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
    }

    public static Map<String, Object> row(
            String hospitalName,
            String type,
            String packName,
            String packageMaterial,
            int instrumentCount,
            int packCount,
            double unitPrice,
            double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", hospitalName);
        row.put("department", "手术室");
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", packageMaterial);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }

    public static Map<String, Object> rowFromFixture(JsonNode fixture) {
        JsonNode row = fixture.path("row");
        String hospitalName = fixture.path("hospitalName").asText("");
        Map<String, Object> map = RuleFidelityTestSupport.rowFromJson(row, hospitalName);
        if (fixture.hasNonNull("customerCode")) {
            map.put("customerCode", fixture.path("customerCode").asText());
        }
        return map;
    }

    public static PricingRuleCompiler mockCompiler(Customer customer, List<CustomerProductRule> rules) {
        CustomerResolver customerResolver = Mockito.mock(CustomerResolver.class);
        CustomerProductRuleMapper productRuleMapper = Mockito.mock(CustomerProductRuleMapper.class);
        CustomerDiscountMapper discountMapper = Mockito.mock(CustomerDiscountMapper.class);
        CustomerBillingPolicyMapper billingPolicyMapper = Mockito.mock(CustomerBillingPolicyMapper.class);
        CustomerBillingRuleGroupMapper ruleGroupMapper = Mockito.mock(CustomerBillingRuleGroupMapper.class);
        ProductVariantMapper productVariantMapper = Mockito.mock(ProductVariantMapper.class);
        ProductMapper productMapper = Mockito.mock(ProductMapper.class);
        ProductMatchRuleMapper productMatchRuleMapper = Mockito.mock(ProductMatchRuleMapper.class);
        RuleSchemaValidator ruleSchemaValidator = Mockito.mock(RuleSchemaValidator.class);

        when(ruleSchemaValidator.validateJsonNode(Mockito.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of(customer.getCanonicalName()));
        when(productRuleMapper.selectByCustomerId(customer.getId())).thenReturn(rules);
        when(discountMapper.selectByCustomerId(customer.getId())).thenReturn(List.of());
        when(billingPolicyMapper.selectByCustomerId(customer.getId())).thenReturn(List.of());
        when(ruleGroupMapper.selectByCustomerIdAndCode(anyLong(), anyString())).thenReturn(null);

        return new PricingRuleCompiler(
                customerResolver,
                productRuleMapper,
                ruleGroupMapper,
                billingPolicyMapper,
                productVariantMapper,
                discountMapper,
                productMapper,
                productMatchRuleMapper,
                ruleSchemaValidator
        );
    }

    private static Customer toCustomer(JsonNode node) {
        Customer customer = new Customer();
        customer.setId(Math.abs(node.path("code").asText("X").hashCode()) % 1_000_000L + 1L);
        customer.setCode(node.path("code").asText());
        customer.setCanonicalName(node.path("name").asText(node.path("code").asText()));
        if (node.hasNonNull("billingEnabled")) {
            customer.setBillingEnabled(node.path("billingEnabled").asBoolean());
        } else {
            String mode = node.path("billingPricingMode").asText("standard");
            customer.setBillingEnabled("hybrid".equalsIgnoreCase(mode)
                    || "special_only".equalsIgnoreCase(mode)
                    || node.path("active_rule_count").asInt(0) > 0);
        }
        customer.setBillingPricingMode(node.path("billingPricingMode").asText("standard"));
        if (node.hasNonNull("pathOverride")) {
            customer.setPathOverride(node.path("pathOverride").isTextual()
                    ? node.path("pathOverride").asText()
                    : node.path("pathOverride").toString());
        }
        if (node.hasNonNull("standardPricingOverride")) {
            customer.setStandardPricingOverride(node.path("standardPricingOverride").toString());
        }
        return customer;
    }

    private static List<CustomerProductRule> toProductRules(JsonNode rulesNode, Long customerId) {
        List<CustomerProductRule> rules = new ArrayList<>();
        if (!rulesNode.isArray()) {
            return rules;
        }
        for (JsonNode ruleNode : rulesNode) {
            if (!ruleNode.path("isActive").asBoolean(true)) {
                continue;
            }
            CustomerProductRule rule = new CustomerProductRule();
            rule.setId(RULE_ID.incrementAndGet());
            rule.setCustomerId(customerId);
            rule.setIsActive(true);
            rule.setRuleType(ruleNode.path("ruleType").asText());
            rule.setName(ruleNode.path("name").asText());
            if (ruleNode.hasNonNull("priority")) {
                rule.setPriority(ruleNode.path("priority").asInt());
            }
            if (ruleNode.hasNonNull("price")) {
                rule.setPrice(BigDecimal.valueOf(ruleNode.path("price").asDouble()));
            }
            if (ruleNode.hasNonNull("fee")) {
                rule.setFee(BigDecimal.valueOf(ruleNode.path("fee").asDouble()));
            }
            if (ruleNode.hasNonNull("multiplier")) {
                rule.setMultiplier(BigDecimal.valueOf(ruleNode.path("multiplier").asDouble()));
            }
            if (ruleNode.hasNonNull("threshold")) {
                rule.setThreshold(ruleNode.path("threshold").asInt());
            }
            if (ruleNode.hasNonNull("foldRatio")) {
                rule.setFoldRatio(BigDecimal.valueOf(ruleNode.path("foldRatio").asDouble()));
            }
            if (ruleNode.hasNonNull("minInstrumentCount")) {
                rule.setMinInstrumentCount(ruleNode.path("minInstrumentCount").asInt());
            }
            if (ruleNode.hasNonNull("maxInstrumentCount")) {
                rule.setMaxInstrumentCount(ruleNode.path("maxInstrumentCount").asInt());
            }
            if (ruleNode.hasNonNull("temperature")) {
                rule.setTemperature(ruleNode.path("temperature").asText());
            }
            if (ruleNode.hasNonNull("matchMode")) {
                rule.setMatchMode(ruleNode.path("matchMode").asText());
            }
            if (ruleNode.hasNonNull("skipPackaging")) {
                rule.setSkipPackaging(ruleNode.path("skipPackaging").asBoolean());
            }
            if (ruleNode.hasNonNull("skipDiscount")) {
                rule.setSkipDiscount(ruleNode.path("skipDiscount").asBoolean());
            }
            if (ruleNode.hasNonNull("minBagSizeInclusive")) {
                rule.setMinBagSizeInclusive(ruleNode.path("minBagSizeInclusive").asInt());
            }
            if (ruleNode.hasNonNull("maxBagSizeExclusive")) {
                rule.setMaxBagSizeExclusive(ruleNode.path("maxBagSizeExclusive").asInt());
            }
            if (ruleNode.has("keywords")) {
                rule.setKeywords(toJsonArrayString(ruleNode.path("keywords")));
            }
            if (ruleNode.has("excludeKeywords")) {
                rule.setExcludeKeywords(toJsonArrayString(ruleNode.path("excludeKeywords")));
            }
            if (ruleNode.hasNonNull("conditionsJson")) {
                rule.setConditionsJson(ruleNode.get("conditionsJson").asText());
            }
            if (ruleNode.has("acceptedPrices")) {
                rule.setAcceptedPrices(ruleNode.path("acceptedPrices").toString());
            }
            rules.add(rule);
        }
        return rules;
    }

    private static String toJsonArrayString(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return "[]";
        }
        ArrayNode copy = MAPPER.createArrayNode();
        arrayNode.forEach(copy::add);
        return copy.toString();
    }

    public static List<JsonNode> fixturesByType(String sc11Type) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode fixture : fixtures().path("fixtures")) {
            if (sc11Type.equals(fixture.path("sc11Type").asText())) {
                out.add(fixture);
            }
        }
        return out;
    }

    public static Map<String, Integer> fixtureTypeCounts() throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode fixture : fixtures().path("fixtures")) {
            if (!isRunnableFixture(fixture)) {
                continue;
            }
            String type = fixture.path("sc11Type").asText();
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    public static boolean isRunnableFixture(JsonNode fixture) {
        if (fixture.path("skipParameterized").asBoolean(false)) {
            return false;
        }
        if (fixture.has("valid") && !fixture.path("valid").asBoolean(true)) {
            return false;
        }
        String customerCode = fixture.path("customerCode").asText("");
        if (customerCode.isBlank()) {
            return false;
        }
        return true;
    }

    public static Map<String, Integer> confirmedFixtureTypeCounts() throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode fixture : fixtures().path("fixtures")) {
            if (!isRunnableFixture(fixture)) {
                continue;
            }
            if (!"confirmed".equals(fixture.path("billingEvidence").asText())) {
                continue;
            }
            String type = fixture.path("sc11Type").asText();
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    public static List<String> sc11TypesMissingConfirmedEvidence() throws Exception {
        List<String> missing = new ArrayList<>();
        for (String type : List.of(
                "SC11-T01", "SC11-T02", "SC11-T03", "SC11-T03b", "SC11-T04", "SC11-T04b",
                "SC11-T05", "SC11-T06", "SC11-T07", "SC11-T08", "SC11-T09", "SC11-T10",
                "SC11-T11", "SC11-T12", "SC11-T13", "SC11-T14", "SC11-T15", "SC11-T16")) {
            if (confirmedFixtureTypeCounts().getOrDefault(type, 0) < 1) {
                missing.add(type);
            }
        }
        return missing;
    }
}
