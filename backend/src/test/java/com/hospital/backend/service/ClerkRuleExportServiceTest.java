package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkRuleExportServiceTest {

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
