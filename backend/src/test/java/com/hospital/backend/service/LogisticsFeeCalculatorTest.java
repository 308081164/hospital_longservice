package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerBillingPolicy;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import com.hospital.backend.mapper.CustomerBillingRuleGroupMapper;
import com.hospital.backend.mapper.CustomerDiscountMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.ProductMapper;
import com.hospital.backend.mapper.ProductMatchRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsFeeCalculatorTest {

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
    private ProductMapper productMapper;
    @Mock
    private ProductMatchRuleMapper productMatchRuleMapper;
    @Mock
    private RuleSchemaValidator ruleSchemaValidator;
    @Mock
    private CustomerBillingRuleGroupMapper ruleGroupMapper;

    @InjectMocks
    private PricingRuleCompiler compiler;

    @BeforeEach
    void stubDefaults() {
        when(ruleSchemaValidator.validateJsonNode(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(RuleSchemaValidator.ValidationResult.ok());
        when(billingPolicyMapper.selectByCustomerId(anyLong())).thenReturn(List.of());
        when(productRuleMapper.selectByCustomerId(anyLong())).thenReturn(List.of());
        when(discountMapper.selectByCustomerId(anyLong())).thenReturn(List.of());
    }

    @Test
    void compilesLogisticsPolicyIntoCustomerOverrides() throws Exception {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCanonicalName("省二南岗");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("省二南岗")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("省二南岗"));

        CustomerBillingPolicy logisticsPolicy = new CustomerBillingPolicy();
        logisticsPolicy.setId(1001L);
        logisticsPolicy.setCustomerId(10L);
        logisticsPolicy.setPolicyType("LOGISTICS");
        logisticsPolicy.setName("南岗物流");
        logisticsPolicy.setParams("{\"feePerTrip\":80.5}");
        logisticsPolicy.setPriority(10);
        logisticsPolicy.setIsActive(true);
        when(billingPolicyMapper.selectByCustomerId(10L)).thenReturn(List.of(logisticsPolicy));

        JsonNode compiled = compiler.compile(MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap()), "省二南岗");

        assertThat(compiled.path("billingPolicies")).hasSize(1);
        assertThat(compiled.path("billingPolicies").get(0).path("policyType").asText()).isEqualTo("LOGISTICS");
        assertThat(compiled.path("customerOverrides").path("logisticsFeePerTrip").asDouble()).isEqualTo(80.5);
        assertThat(compiled.path("customerOverrides").path("logisticsPolicyId").asLong()).isEqualTo(1001L);
    }

    @Test
    void customerLogisticsPolicyOverridesGlobalFeePerTrip() throws Exception {
        Customer customer = new Customer();
        customer.setId(11L);
        customer.setCanonicalName("呼兰中医");
        customer.setBillingEnabled(true);
        when(customerResolver.resolveByName("呼兰中医")).thenReturn(Optional.of(customer));
        when(customerResolver.hospitalNamesForCustomer(customer)).thenReturn(List.of("呼兰中医"));

        CustomerBillingPolicy logisticsPolicy = new CustomerBillingPolicy();
        logisticsPolicy.setId(1101L);
        logisticsPolicy.setCustomerId(11L);
        logisticsPolicy.setPolicyType("LOGISTICS");
        logisticsPolicy.setParams("{\"feePerTrip\":185}");
        logisticsPolicy.setPriority(10);
        logisticsPolicy.setIsActive(true);
        when(billingPolicyMapper.selectByCustomerId(11L)).thenReturn(List.of(logisticsPolicy));

        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode logistics = (ObjectNode) base.path("logistics");
        logistics.put("enabled", true);
        logistics.put("feePerTrip", 50.0);

        JsonNode compiled = compiler.compile(base, "呼兰中医");
        List<Map<String, Object>> rows = List.of(
                Map.of("deliveryDate", "2026-07-01"),
                Map.of("deliveryDate", "2026-07-02"),
                Map.of("deliveryDate", "2026-07-01 10:00"));

        Optional<LogisticsFeeCalculator.Result> result = LogisticsFeeCalculator.compute(compiled, rows);

        assertThat(result).isPresent();
        assertThat(result.get().tripCount()).isEqualTo(2);
        assertThat(result.get().feePerTrip()).isEqualTo(185.0);
        assertThat(result.get().totalFee()).isEqualTo(370.0);
        assertThat(result.get().feeSource()).isEqualTo("customer");
        assertThat(result.get().policyId()).isEqualTo(1101L);
    }
}
