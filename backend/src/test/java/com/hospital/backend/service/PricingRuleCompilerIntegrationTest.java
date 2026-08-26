package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerBillingPolicy;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerBillingRuleGroupMapper;
import com.hospital.backend.mapper.CustomerDiscountMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import com.hospital.backend.mapper.ProductVariantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingRuleCompilerIntegrationTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    @Mock
    private CustomerResolver customerResolver;
    @Mock
    private CustomerProductRuleMapper productRuleMapper;
    @Mock
    private CustomerDiscountMapper discountMapper;
    @Mock
    private CustomerBillingPolicyMapper billingPolicyMapper;
    @Mock
    private CustomerBillingRuleGroupMapper ruleGroupMapper;
    @Mock
    private ProductVariantMapper productVariantMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductMatchRuleMapper productMatchRuleMapper;
    @Mock
    private RuleSchemaValidator ruleSchemaValidator;

    @InjectMocks
    private PricingRuleCompiler compiler;

    @BeforeEach
    void stubBillingPoliciesEmpty() {
        when(billingPolicyMapper.selectByCustomerId(anyLong())).thenReturn(List.of());
        when(ruleGroupMapper.selectByCustomerIdAndCode(anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
    }

    @Test
    void mergesCustomerExtraFeeIntoCompiledRules() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCanonicalName("黑龙江总工会医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("黑龙江总工会医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("黑龙江总工会医院"));
        when(discountMapper.selectByCustomerId(1L)).thenReturn(List.of());

        CustomerProductRule extraFee = new CustomerProductRule();
        extraFee.setIsActive(true);
        extraFee.setRuleType("EXTRA_FEE");
        extraFee.setName("镜头租借公司筐加收");
        extraFee.setKeywords("[\"镜头\"]");
        extraFee.setFee(BigDecimal.valueOf(8));
        when(productRuleMapper.selectByCustomerId(1L)).thenReturn(List.of(extraFee));

        JsonNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        JsonNode compiled = compiler.compile(base, "黑龙江总工会医院");

        JsonNode extraFees = compiled.path("specialRules").path("extraFees");
        assertThat(extraFees.isArray()).isTrue();
        assertThat(extraFees).hasSize(1);
        assertThat(extraFees.get(0).path("fee").asDouble()).isEqualTo(8.0);
        assertThat(extraFees.get(0).path("keywords").get(0).asText()).isEqualTo("镜头");

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "黑龙江总工会医院",
                "type", "单包装包(老肯低温)",
                "packName", "30°镜头，镜鞘-2（带转换帽）/Z2060",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 4,
                "packCount", 2,
                "unitPrice", 52,
                "totalPrice", 104
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(52.0);
        assertThat(result.correctedTotalPrice).isEqualTo(104.0);
        assertThat(result.notes).anyMatch(note -> note.contains("镜头"));
    }

    @Test
    void customerFixedPricePrecedesGenericAndWinsFirstMatch() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setCanonicalName("测试特色医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("测试特色医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("测试特色医院"));
        when(discountMapper.selectByCustomerId(2L)).thenReturn(List.of());

        CustomerProductRule customerFixed = new CustomerProductRule();
        customerFixed.setIsActive(true);
        customerFixed.setRuleType("FIXED_PRICE");
        customerFixed.setName("客户特色固定价");
        customerFixed.setKeywords("[\"空心钉\"]");
        customerFixed.setPrice(BigDecimal.valueOf(99.0));
        customerFixed.setSkipPackaging(true);
        when(productRuleMapper.selectByCustomerId(2L)).thenReturn(List.of(customerFixed));

        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode specialRules = (ObjectNode) base.path("specialRules");
        ArrayNode fixedPrices = specialRules.putArray("fixedPrices");
        ObjectNode generic = fixedPrices.addObject();
        generic.put("name", "通用固定价");
        generic.put("price", 50.0);
        generic.put("skipPackaging", true);
        generic.putArray("keywords").add("空心钉");
        generic.putArray("hospitals").add("测试特色医院");

        JsonNode compiled = compiler.compile(base, "测试特色医院");
        JsonNode compiledFixed = compiled.path("specialRules").path("fixedPrices");
        assertThat(compiledFixed).hasSize(2);
        assertThat(compiledFixed.get(0).path("name").asText()).isEqualTo("客户特色固定价");
        assertThat(compiledFixed.get(0).path("price").asDouble()).isEqualTo(99.0);
        assertThat(compiledFixed.get(1).path("name").asText()).isEqualTo("通用固定价");

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "测试特色医院",
                "type", "额外包(纸塑袋)",
                "packName", "3.6空心钉-2",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 99,
                "totalPrice", 99
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(99.0);
        assertThat(result.notes).anyMatch(note -> note.contains("客户特色固定价"));
        assertThat(result.notes).noneMatch(note -> note.contains("通用固定价"));
    }

    @Test
    void customerExtraFeeAndMultiplierPrecedeGenericRules() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(3L);
        customer.setCanonicalName("倍率加费医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("倍率加费医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("倍率加费医院"));
        when(discountMapper.selectByCustomerId(3L)).thenReturn(List.of());

        CustomerProductRule multiplier = new CustomerProductRule();
        multiplier.setIsActive(true);
        multiplier.setRuleType("MULTIPLIER");
        multiplier.setName("客户特色倍率");
        multiplier.setKeywords("[\"特殊包\"]");
        multiplier.setMultiplier(BigDecimal.valueOf(1.5));

        CustomerProductRule extraFee = new CustomerProductRule();
        extraFee.setIsActive(true);
        extraFee.setRuleType("EXTRA_FEE");
        extraFee.setName("客户特色加收");
        extraFee.setKeywords("[\"特殊包\"]");
        extraFee.setFee(BigDecimal.valueOf(12));

        when(productRuleMapper.selectByCustomerId(3L)).thenReturn(List.of(multiplier, extraFee));

        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode specialRules = (ObjectNode) base.path("specialRules");

        ObjectNode genericMultiplier = specialRules.putArray("priceMultipliers").addObject();
        genericMultiplier.put("name", "通用倍率");
        genericMultiplier.put("multiplier", 2.0);
        genericMultiplier.putArray("keywords").add("特殊包");
        genericMultiplier.putArray("hospitals").add("倍率加费医院");

        ObjectNode genericFee = specialRules.withArray("extraFees").addObject();
        genericFee.put("name", "通用加收");
        genericFee.put("fee", 3.0);
        genericFee.putArray("keywords").add("特殊包");
        genericFee.putArray("hospitals").add("倍率加费医院");

        JsonNode compiled = compiler.compile(base, "倍率加费医院");

        JsonNode multipliers = compiled.path("specialRules").path("priceMultipliers");
        assertThat(multipliers).hasSize(2);
        assertThat(multipliers.get(0).path("name").asText()).isEqualTo("客户特色倍率");
        assertThat(multipliers.get(1).path("name").asText()).isEqualTo("通用倍率");

        JsonNode extraFees = compiled.path("specialRules").path("extraFees");
        assertThat(extraFees).hasSize(2);
        assertThat(extraFees.get(0).path("name").asText()).isEqualTo("客户特色加收");
        assertThat(extraFees.get(0).path("fee").asDouble()).isEqualTo(12.0);
        assertThat(extraFees.get(1).path("name").asText()).isEqualTo("通用加收");
    }

    @Test
    void customerFoldRuleCompilesAndApplies() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(4L);
        customer.setCanonicalName("折算测试医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("折算测试医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("折算测试医院"));
        when(discountMapper.selectByCustomerId(4L)).thenReturn(List.of());

        CustomerProductRule foldRule = new CustomerProductRule();
        foldRule.setId(41L);
        foldRule.setIsActive(true);
        foldRule.setRuleType("FOLD");
        foldRule.setName("客户小件折算");
        foldRule.setKeywords("[\"机扩针\"]");
        foldRule.setThreshold(5);
        foldRule.setFoldRatio(BigDecimal.valueOf(5));
        when(productRuleMapper.selectByCustomerId(4L)).thenReturn(List.of(foldRule));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "折算测试医院");
        JsonNode foldRules = compiled.path("specialRules").path("foldRules");
        assertThat(foldRules).hasSize(3);
        assertThat(foldRules.get(0).path("name").asText()).isEqualTo("客户小件折算");
        assertThat(foldRules.get(0).path("foldRatio").asDouble()).isEqualTo(5.0);

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "折算测试医院",
                "type", "额外包(纸塑袋)",
                "packName", "机扩针-20/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 20,
                "packCount", 1,
                "unitPrice", 22,
                "totalPrice", 22
        ));
        assertThat(result.notes).anyMatch(note -> note.contains("客户小件折算"));
    }

    @Test
    void billingDisabledSkipsCustomerProductRules() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(5L);
        customer.setCanonicalName("关闭特色医院");
        customer.setBillingEnabled(false);
        when(customerResolver.resolveByName("关闭特色医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("关闭特色医院"));
        when(discountMapper.selectByCustomerId(5L)).thenReturn(List.of());

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "关闭特色医院");
        assertThat(compiled.path("billingProfile").path("enabled").asBoolean()).isFalse();
        JsonNode fixedPrices = compiled.path("specialRules").path("fixedPrices");
        assertThat(fixedPrices).isNotEmpty();
        assertThat(fixedPrices).allSatisfy(node -> assertThat(node.has("ruleId")).isFalse());
    }

    @Test
    void compilesExcludeKeywordsAndAcceptedPrices() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(6L);
        customer.setCanonicalName("多报价医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("多报价医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("多报价医院"));
        when(discountMapper.selectByCustomerId(6L)).thenReturn(List.of());

        CustomerProductRule multiPrice = new CustomerProductRule();
        multiPrice.setId(61L);
        multiPrice.setIsActive(true);
        multiPrice.setRuleType("FIXED_PRICE");
        multiPrice.setMatchMode("any_price");
        multiPrice.setName("小腔包");
        multiPrice.setKeywords("[\"小腔包\"]");
        multiPrice.setPrice(BigDecimal.valueOf(71));
        multiPrice.setAcceptedPrices("[71,76.5]");
        when(productRuleMapper.selectByCustomerId(6L)).thenReturn(List.of(multiPrice));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "多报价医院");
        JsonNode ruleNode = compiled.path("specialRules").path("fixedPrices").get(0);
        assertThat(ruleNode.path("matchMode").asText()).isEqualTo("any_price");
        assertThat(ruleNode.path("excludeKeywords")).isEmpty();
        assertThat(ruleNode.path("acceptedPrices")).hasSize(2);
        assertThat(ruleNode.path("ruleId").asLong()).isEqualTo(61L);
    }

    @Test
    void compilesBillingPoliciesWithTemperatureScopes() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(7L);
        customer.setCanonicalName("维多利亚医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("维多利亚医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("维多利亚医院"));
        when(discountMapper.selectByCustomerId(7L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(7L)).thenReturn(List.of());

        CustomerBillingPolicy htPolicy = new CustomerBillingPolicy();
        htPolicy.setId(701L);
        htPolicy.setCustomerId(7L);
        htPolicy.setPolicyType("DISCOUNT");
        htPolicy.setName("高温5折");
        htPolicy.setScope("{\"temperature\":\"HT\"}");
        htPolicy.setParams("{\"rate\":0.5,\"skipWhenFixedPrice\":true}");
        htPolicy.setPriority(10);
        htPolicy.setIsActive(true);

        CustomerBillingPolicy ltPolicy = new CustomerBillingPolicy();
        ltPolicy.setId(702L);
        ltPolicy.setCustomerId(7L);
        ltPolicy.setPolicyType("DISCOUNT");
        ltPolicy.setName("低温7折");
        ltPolicy.setScope("{\"temperature\":\"LT\"}");
        ltPolicy.setParams("{\"rate\":0.7,\"skipWhenFixedPrice\":true}");
        ltPolicy.setPriority(20);
        ltPolicy.setIsActive(true);

        when(billingPolicyMapper.selectByCustomerId(7L)).thenReturn(List.of(htPolicy, ltPolicy));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "维多利亚医院");
        JsonNode policies = compiled.path("billingPolicies");
        assertThat(policies).hasSize(2);
        assertThat(policies.get(0).path("scope").path("temperature").asText()).isEqualTo("HT");
        assertThat(policies.get(0).path("params").path("rate").asDouble()).isEqualTo(0.5);

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult htResult = engine.processRow(Map.of(
                "hospitalName", "维多利亚医院",
                "type", "额外包(纸塑袋)",
                "packName", "普通器械-4/Z7526",
                "packageMaterial", "高温纸塑袋20cm",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 11,
                "totalPrice", 11
        ));
        assertThat(htResult.notes).anyMatch(note -> note.contains("5折") || note.contains("0.5"));

        PricingEngine.ProcessedResult ltResult = engine.processRow(Map.of(
                "hospitalName", "维多利亚医院",
                "type", "单包装包(老肯低温)",
                "packName", "普通器械-1/Z7526",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 19.6,
                "totalPrice", 19.6
        ));
        assertThat(ltResult.notes).anyMatch(note -> note.contains("7折") || note.contains("0.7"));
    }

    @Test
    void compilesLogisticsPolicyIntoBillingPoliciesAndOverrides() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(8L);
        customer.setCanonicalName("省二松北");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("省二松北")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("省二松北"));
        when(discountMapper.selectByCustomerId(8L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(8L)).thenReturn(List.of());

        CustomerBillingPolicy logisticsPolicy = new CustomerBillingPolicy();
        logisticsPolicy.setId(801L);
        logisticsPolicy.setCustomerId(8L);
        logisticsPolicy.setPolicyType("LOGISTICS");
        logisticsPolicy.setName("松北物流");
        logisticsPolicy.setParams("{\"feePerTrip\":80.5}");
        logisticsPolicy.setPriority(10);
        logisticsPolicy.setIsActive(true);
        when(billingPolicyMapper.selectByCustomerId(8L)).thenReturn(List.of(logisticsPolicy));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "省二松北");
        assertThat(compiled.path("billingPolicies").get(0).path("params").path("feePerTrip").asDouble()).isEqualTo(80.5);
        assertThat(compiled.path("customerOverrides").path("logisticsFeePerTrip").asDouble()).isEqualTo(80.5);
    }

    @Test
    void compilesBillingProfileWithPricingModeAndPathOverride() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(9L);
        customer.setCanonicalName("道外人民");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("standard");
        customer.setPathOverride("{\"disableLowTemp\":true,\"forceHighTempUnitPrice\":3}");
        when(customerResolver.resolveByName("道外人民")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("道外人民"));
        when(billingPolicyMapper.selectByCustomerId(9L)).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(9L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(9L)).thenReturn(List.of());

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "道外人民");
        assertThat(compiled.path("billingProfile").path("pricingMode").asText()).isEqualTo("hybrid");
        assertThat(compiled.path("billingProfile").path("pathOverride").path("disableLowTemp").asBoolean()).isTrue();
        assertThat(compiled.path("billingProfile").path("pathOverride").path("forceHighTempUnitPrice").asDouble()).isEqualTo(3.0);
    }

    @Test
    void compilesMonthlySettlementPolicy() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCanonicalName("维多利亚");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("维多利亚")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("维多利亚"));
        when(discountMapper.selectByCustomerId(10L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(10L)).thenReturn(List.of());

        CustomerBillingPolicy monthlyPolicy = new CustomerBillingPolicy();
        monthlyPolicy.setId(1001L);
        monthlyPolicy.setCustomerId(10L);
        monthlyPolicy.setPolicyType("MONTHLY_SETTLEMENT");
        monthlyPolicy.setName("月低消");
        monthlyPolicy.setParams("{\"minCharge\":8000}");
        monthlyPolicy.setPriority(10);
        monthlyPolicy.setIsActive(true);
        when(billingPolicyMapper.selectByCustomerId(10L)).thenReturn(List.of(monthlyPolicy));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "维多利亚");
        assertThat(compiled.path("billingPolicies").get(0).path("policyType").asText()).isEqualTo("MONTHLY_SETTLEMENT");
        assertThat(compiled.path("billingPolicies").get(0).path("params").path("minCharge").asDouble()).isEqualTo(8000.0);
    }

    @Test
    void billingDisabledSkipsCustomerRulesInEngine() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(11L);
        customer.setCanonicalName("关闭特色引擎医院");
        customer.setBillingEnabled(false);
        when(customerResolver.resolveByName("关闭特色引擎医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("关闭特色引擎医院"));
        when(discountMapper.selectByCustomerId(11L)).thenReturn(List.of());

        CustomerProductRule fixed = new CustomerProductRule();
        fixed.setIsActive(true);
        fixed.setRuleType("FIXED_PRICE");
        fixed.setName("不应生效");
        fixed.setKeywords("[\"空心钉\"]");
        fixed.setPrice(BigDecimal.valueOf(99));
        when(productRuleMapper.selectByCustomerId(11L)).thenReturn(List.of(fixed));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "关闭特色引擎医院");
        assertThat(compiled.path("billingProfile").path("enabled").asBoolean()).isFalse();
        JsonNode fixedPrices = compiled.path("specialRules").path("fixedPrices");
        assertThat(fixedPrices).isNotEmpty();
        assertThat(fixedPrices).allSatisfy(node -> assertThat(node.has("ruleId")).isFalse());

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "关闭特色引擎医院",
                "type", "额外包(纸塑袋)",
                "packName", "3.6空心钉-2",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 99,
                "totalPrice", 99
        ));
        assertThat(result.notes).noneMatch(note -> note.contains("不应生效"));
    }

    @Test
    void compilesExcludeKeywordsAndAppliesInEngine() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCanonicalName("省二院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("省二院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("省二院"));
        when(discountMapper.selectByCustomerId(12L)).thenReturn(List.of());

        CustomerProductRule nailRule = new CustomerProductRule();
        nailRule.setId(120L);
        nailRule.setIsActive(true);
        nailRule.setRuleType("FIXED_PRICE");
        nailRule.setName("xx钉");
        nailRule.setKeywords("[\"钉\"]");
        nailRule.setExcludeKeywords("[\"空心钉\"]");
        nailRule.setPrice(BigDecimal.valueOf(200));
        nailRule.setSkipPackaging(true);
        when(productRuleMapper.selectByCustomerId(12L)).thenReturn(List.of(nailRule));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "省二院");
        JsonNode compiledRule = compiled.path("specialRules").path("fixedPrices").get(0);
        assertThat(compiledRule.path("excludeKeywords").get(0).asText()).isEqualTo("空心钉");

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult hollow = engine.processRow(Map.of(
                "hospitalName", "省二院",
                "type", "额外包(纸塑袋)",
                "packName", "3.6空心钉-2",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 2,
                "packCount", 1,
                "unitPrice", 19,
                "totalPrice", 19
        ));
        assertThat(hollow.notes).noneMatch(note -> note.contains("xx钉"));
    }

    @Test
    void compilesSpecialOnlyModeAndAppliesInEngine() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(13L);
        customer.setCanonicalName("仅特色医院");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("special_only");
        when(customerResolver.resolveByName("仅特色医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("仅特色医院"));
        when(billingPolicyMapper.selectByCustomerId(13L)).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(13L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(13L)).thenReturn(List.of());

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "仅特色医院");
        assertThat(compiled.path("billingProfile").path("pricingMode").asText()).isEqualTo("hybrid");

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "仅特色医院",
                "type", "额外包(纸塑袋)",
                "packName", "普通器械-4/Z7526",
                "packageMaterial", "高温纸塑袋20cm",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 22,
                "totalPrice", 22
        ));
        assertThat(result.pricingRule).contains("高温纸塑袋");
    }

    @Test
    void hrbCjSurgicalPackCustomerRuleCompilesAndMatches() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(11L);
        customer.setCode("HRB-CJ");
        customer.setCanonicalName("哈尔滨长健医院");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("standard");
        when(customerResolver.resolveByName("哈尔滨长健医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("哈尔滨长健医院"));
        when(discountMapper.selectByCustomerId(11L)).thenReturn(List.of());
        when(billingPolicyMapper.selectByCustomerId(11L)).thenReturn(List.of());

        CustomerProductRule surgicalPack = new CustomerProductRule();
        surgicalPack.setId(315L);
        surgicalPack.setIsActive(true);
        surgicalPack.setRuleType("PRICE_PER_INSTRUMENT");
        surgicalPack.setName("手术包5.5元/件");
        surgicalPack.setPriority(10);
        surgicalPack.setKeywords("[\"手术包\"]");
        surgicalPack.setTemperature("HT");
        surgicalPack.setPrice(BigDecimal.valueOf(5.5));
        surgicalPack.setSkipPackaging(true);
        surgicalPack.setSkipDiscount(true);
        when(productRuleMapper.selectByCustomerId(11L)).thenReturn(List.of(surgicalPack));

        JsonNode compiled = compiler.compileForCustomer(
                MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), customer);
        JsonNode fixedPrices = compiled.path("specialRules").path("fixedPrices");
        assertThat(fixedPrices).anySatisfy(node ->
                assertThat(node.path("name").asText()).isEqualTo("手术包5.5元/件"));

        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨长健医院",
                "type", "器械包(ZSD)",
                "packName", "手术包（二）",
                "packageMaterial", "高温灭菌无纺布60*60",
                "instrumentCount", 43,
                "packCount", 1,
                "unitPrice", 231,
                "totalPrice", 231
        ));

        assertThat(result.expectedUnitPrice).isEqualTo(236.5);
        assertThat(result.pricingRule).isEqualTo("手术包5.5元/件");
        assertThat(result.matchedRuleId).isEqualTo(315L);
        assertThat(result.status).isEqualTo("warning");
    }

    @Test
    void compilesPathOverrideAndAppliesInEngine() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(14L);
        customer.setCanonicalName("道外人民");
        customer.setBillingEnabled(true);
        customer.setPathOverride("{\"disableLowTemp\":true,\"forceHighTempUnitPrice\":3}");
        when(customerResolver.resolveByName("道外人民")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("道外人民"));
        when(billingPolicyMapper.selectByCustomerId(14L)).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(14L)).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(14L)).thenReturn(List.of());

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "道外人民");
        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "道外人民",
                "type", "单包装包(老肯低温)",
                "packName", "普通器械-4/Z7526",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 12,
                "totalPrice", 12
        ));
        assertThat(result.expectedUnitPrice).isEqualTo(12.0);
        assertThat(result.notes).anyMatch(note -> note.contains("路径覆盖"));
    }

    @Test
    void fnnFoldRuleAppliesPackagingAtOrBelowTenInstruments() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(101L);
        customer.setCanonicalName("方南南医院");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("方南南医院")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("方南南医院"));
        when(discountMapper.selectByCustomerId(101L)).thenReturn(List.of());

        CustomerProductRule foldWithPackaging = foldRule(101L, "方南南小件5合1含包材", null, 10, false);
        CustomerProductRule foldSkipPackaging = foldRule(101L, "方南南小件5合1免包材", 11, null, true);
        when(productRuleMapper.selectByCustomerId(101L)).thenReturn(List.of(foldWithPackaging, foldSkipPackaging));

        JsonNode compiled = compiler.compile(packagingEnabledTemplate(), "方南南医院");
        PricingEngine engine = new PricingEngine(compiled);
        JsonNode compiledNoPackaging = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "方南南医院");
        PricingEngine engineNoPackaging = new PricingEngine(compiledNoPackaging);

        Map<String, Object> smallBatchRow = Map.of(
                "hospitalName", "方南南医院",
                "type", "额外包(纸塑袋)",
                "packName", "机扩针-8/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 8,
                "packCount", 1,
                "unitPrice", 22,
                "totalPrice", 22
        );
        Map<String, Object> largeBatchRow = Map.of(
                "hospitalName", "方南南医院",
                "type", "额外包(纸塑袋)",
                "packName", "机扩针-20/Z7520",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 20,
                "packCount", 1,
                "unitPrice", 22,
                "totalPrice", 22
        );

        PricingEngine.ProcessedResult smallBatch = engine.processRow(smallBatchRow);
        PricingEngine.ProcessedResult largeBatch = engine.processRow(largeBatchRow);
        PricingEngine.ProcessedResult smallWithoutPackaging = engineNoPackaging.processRow(smallBatchRow);
        PricingEngine.ProcessedResult largeWithoutPackaging = engineNoPackaging.processRow(largeBatchRow);

        assertThat(smallBatch.notes).anyMatch(note -> note.contains("方南南小件5合1含包材"));
        assertThat(largeBatch.notes).anyMatch(note -> note.contains("方南南小件5合1免包材"));
        assertThat(largeBatch.expectedUnitPrice).isEqualTo(22.0);
        assertThat(smallBatch.expectedUnitPrice).isGreaterThan(smallWithoutPackaging.expectedUnitPrice);
        assertThat(largeBatch.expectedUnitPrice).isEqualTo(largeWithoutPackaging.expectedUnitPrice);
        assertThat(smallBatch.notes).anyMatch(note -> note.contains("包装收费"));
        assertThat(largeBatch.notes).noneMatch(note -> note.contains("包装收费"));
    }

    @Test
    void meiyiDressingFixedPriceRequiresBagSizeAtLeastTwenty() throws Exception {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());

        Customer customer = new Customer();
        customer.setId(102L);
        customer.setCanonicalName("美意医疗");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("美意医疗")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("美意医疗"));
        when(discountMapper.selectByCustomerId(102L)).thenReturn(List.of());

        CustomerProductRule fixed = new CustomerProductRule();
        fixed.setId(1021L);
        fixed.setIsActive(true);
        fixed.setRuleType("FIXED_PRICE");
        fixed.setName("美意敷料纸塑4元");
        fixed.setKeywords("[\"洞巾\", \"治疗巾\"]");
        fixed.setPrice(BigDecimal.valueOf(4.0));
        fixed.setMinBagSizeInclusive(20);
        fixed.setSkipPackaging(true);
        fixed.setSkipDiscount(true);
        when(productRuleMapper.selectByCustomerId(102L)).thenReturn(List.of(fixed));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "美意医疗");
        PricingEngine engine = new PricingEngine(compiled);

        PricingEngine.ProcessedResult matched = engine.processRow(Map.of(
                "hospitalName", "美意医疗",
                "type", "敷料包(纸塑袋)",
                "packName", "洞巾",
                "packageMaterial", "高温纸塑袋250*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 4,
                "totalPrice", 4
        ));
        assertThat(matched.expectedUnitPrice).isEqualTo(4.0);
        assertThat(matched.pricingRule).isEqualTo("美意敷料纸塑4元");

        PricingEngine.ProcessedResult tooSmall = engine.processRow(Map.of(
                "hospitalName", "美意医疗",
                "type", "敷料包(纸塑袋)",
                "packName", "洞巾",
                "packageMaterial", "高温纸塑袋150*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 4,
                "totalPrice", 4
        ));
        assertThat(tooSmall.pricingRule).isNotEqualTo("美意敷料纸塑4元");
    }

    private CustomerProductRule foldRule(Long customerId, String name, Integer minCount, Integer maxCount,
                                         boolean skipPackaging) {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setId((long) name.hashCode());
        rule.setIsActive(true);
        rule.setRuleType("FOLD");
        rule.setName(name);
        rule.setKeywords("[\"P钻\", \"根管锉\", \"光滑针\", \"机扩针\"]");
        rule.setThreshold(5);
        rule.setFoldRatio(BigDecimal.valueOf(5));
        rule.setMinInstrumentCount(minCount);
        rule.setMaxInstrumentCount(maxCount);
        rule.setSkipPackaging(skipPackaging);
        return rule;
    }

    private JsonNode packagingEnabledTemplate() throws Exception {
        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode packaging = (ObjectNode) base.get("packaging");
        packaging.put("enabled", true);
        ArrayNode items = MAPPER.createArrayNode();
        ObjectNode item = MAPPER.createObjectNode();
        item.put("name", "纸塑袋");
        item.put("chargePerPack", false);
        item.set("keywords", MAPPER.createArrayNode().add("纸塑袋"));
        ObjectNode option = MAPPER.createObjectNode();
        option.put("label", "20cm");
        option.put("price", 3.0);
        option.set("keywords", MAPPER.createArrayNode().add("200"));
        item.set("options", MAPPER.createArrayNode().add(option));
        packaging.set("items", MAPPER.createArrayNode().add(item));
        return base;
    }
}
