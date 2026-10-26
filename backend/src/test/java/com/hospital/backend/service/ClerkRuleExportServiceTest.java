package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClerkRuleExportServiceTest {

    private CustomerResolver customerResolver;
    private ClerkRuleExportService service;

    @BeforeEach
    void setUp() {
        customerResolver = mock(CustomerResolver.class);
        ClerkRuleIndex index = new ClerkRuleIndex();
        service = new ClerkRuleExportService(
                new ClerkRuleCompiler(index),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                index,
                customerResolver);
    }

    @Test
    void settlementMergeUsesArchiveCodeForLogisticsFees() {
        List<Map<String, Object>> twoDays = List.of(
                Map.of("deliveryDate", "2026-06-01"),
                Map.of("deliveryDate", "2026-06-02"));
        ObjectNode legacy = JsonUtils.getObjectMapper().createObjectNode();
        legacy.putObject("logistics").put("enabled", true).put("feePerTrip", 50);

        when(customerResolver.resolveByName("无关医院")).thenReturn(Optional.empty());
        JsonNode untouched = service.mergeCompiledForSettlement("无关医院", legacy);
        assertThat(LogisticsFeeCalculator.compute(untouched, twoDays).orElseThrow().totalFee()).isEqualTo(100.0);

        when(customerResolver.resolveByName("黑龙江省山河屯林区人民医院"))
                .thenReturn(Optional.of(customer("SHANTUN-RM", "黑龙江省山河屯林区人民医院")));
        JsonNode shantun = service.mergeCompiledForSettlement("黑龙江省山河屯林区人民医院", legacy);
        assertThat(LogisticsFeeCalculator.compute(shantun, twoDays).orElseThrow().totalFee()).isEqualTo(0.0);

        when(customerResolver.resolveByName("新中新医院"))
                .thenReturn(Optional.of(customer("XZX-YY", "新中新医院")));
        JsonNode xinzhongxin = service.mergeCompiledForSettlement("新中新医院", legacy);
        assertThat(LogisticsFeeCalculator.compute(xinzhongxin, twoDays).orElseThrow().totalFee()).isEqualTo(75.0);

        when(customerResolver.resolveByName("哈尔滨市南岗区黎增亮口腔科诊所"))
                .thenReturn(Optional.of(customer("LZL-KQ", "哈尔滨市南岗区黎增亮口腔科诊所")));
        JsonNode lizengliang = service.mergeCompiledForSettlement("哈尔滨市南岗区黎增亮口腔科诊所", legacy);
        assertThat(LogisticsFeeCalculator.compute(lizengliang, twoDays).orElseThrow().totalFee()).isEqualTo(350.0);
    }

    @Test
    void renshengWithoutCustomerArchiveStillShowsEightFoldRemark() {
        when(customerResolver.resolveByName("哈尔滨仁胜医院")).thenReturn(Optional.empty());
        when(customerResolver.resolveByName("黑龙江维多利亚妇产医院")).thenReturn(Optional.empty());

        JsonNode rensheng = service.mergeCompiledForSettlement(
                "哈尔滨仁胜医院", JsonUtils.getObjectMapper().createObjectNode());
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applySettlementDiscount(
                rensheng, "", "", "", "哈尔滨仁胜医院", 4368);
        assertThat(discount.price()).isEqualTo(3494.4);
        assertThat(discount.note()).isEqualTo("8折优惠");

        JsonNode victoria = service.mergeCompiledForSettlement(
                "黑龙江维多利亚妇产医院", JsonUtils.getObjectMapper().createObjectNode());
        assertThat(BillingPolicyApplier.applySettlementDiscount(
                victoria, "", "", "", "黑龙江维多利亚妇产医院", 1000)).isNull();
    }

    @Test
    void legacyDongdaCodeStillAppliesClerkSettlementDiscount() {
        when(customerResolver.resolveByName("黑龙江东大肛肠医院"))
                .thenReturn(Optional.of(customer("DD-DC", "黑龙江东大肛肠")));
        when(customerResolver.resolveByName("黑龙江东大肛肠"))
                .thenReturn(Optional.of(customer("DD-DC", "黑龙江东大肛肠")));

        JsonNode fullName = service.mergeCompiledForSettlement(
                "黑龙江东大肛肠医院", JsonUtils.getObjectMapper().createObjectNode());
        JsonNode shortName = service.mergeCompiledForSettlement(
                "黑龙江东大肛肠", JsonUtils.getObjectMapper().createObjectNode());

        assertThat(BillingPolicyApplier.applySettlementDiscount(
                fullName, "", "", "", "黑龙江东大肛肠医院", 1000).price()).isEqualTo(750.0);
        assertThat(BillingPolicyApplier.applySettlementDiscount(
                shortName, "", "", "", "黑龙江东大肛肠", 1000).price()).isEqualTo(750.0);
        assertThat(BillingPolicyApplier.applyBillDetailDiscounts(
                fullName, "高温", "治疗包", "无纺布", "黑龙江东大肛肠医院", 100, 1, false, false))
                .isNull();
    }

    @Test
    void mergeKeepsLegacyExportDiscountWhenClerkOnlyHasSettlementLogistics() {
        ObjectNode legacy = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode legacyPolicies = JsonUtils.getObjectMapper().createArrayNode();
        legacyPolicies.add(discountPolicy("export_only", 0.75, "太平导出阶段折扣"));
        legacy.set("billingPolicies", legacyPolicies);

        ObjectNode clerk = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode clerkPolicies = JsonUtils.getObjectMapper().createArrayNode();
        clerkPolicies.add(logisticsPolicy("settlement_only", 0));
        clerk.set("billingPolicies", clerkPolicies);
        clerk.putArray("clerkRules");

        ObjectNode merged = ClerkRuleExportService.mergeCompiledNodes(legacy, clerk);
        ArrayNode policies = (ArrayNode) merged.path("billingPolicies");
        assertThat(policies).hasSize(2);
        assertThat(policies.get(0).path("params").path("applyStage").asText()).isEqualTo("settlement_only");
        assertThat(policies.get(1).path("params").path("applyStage").asText()).isEqualTo("export_only");
    }

    @Test
    void mergePrefersClerkSettlementDiscountOverLegacySettlementDiscount() {
        ObjectNode legacy = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode legacyPolicies = JsonUtils.getObjectMapper().createArrayNode();
        legacyPolicies.add(discountPolicy("settlement_only", 0.75, "legacy结款75折"));
        legacy.set("billingPolicies", legacyPolicies);

        ObjectNode clerk = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode clerkPolicies = JsonUtils.getObjectMapper().createArrayNode();
        clerkPolicies.add(discountPolicy("settlement_only", 0.8, "clerk结款8折"));
        clerk.set("billingPolicies", clerkPolicies);

        ObjectNode merged = ClerkRuleExportService.mergeCompiledNodes(legacy, clerk);
        ArrayNode policies = (ArrayNode) merged.path("billingPolicies");
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).path("params").path("rate").asDouble()).isEqualTo(0.8);
    }

    private static Customer customer(String code, String name) {
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(name);
        return customer;
    }

    private static ObjectNode discountPolicy(String applyStage, double rate, String name) {
        ObjectNode policy = JsonUtils.getObjectMapper().createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", name);
        ObjectNode params = JsonUtils.getObjectMapper().createObjectNode();
        params.put("applyStage", applyStage);
        params.put("rate", rate);
        policy.set("params", params);
        return policy;
    }

    private static ObjectNode logisticsPolicy(String applyStage, double feePerTrip) {
        ObjectNode policy = JsonUtils.getObjectMapper().createObjectNode();
        policy.put("policyType", "LOGISTICS");
        policy.put("name", "不收物流费");
        ObjectNode params = JsonUtils.getObjectMapper().createObjectNode();
        params.put("applyStage", applyStage);
        params.put("feePerTrip", feePerTrip);
        policy.set("params", params);
        return policy;
    }
}
