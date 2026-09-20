package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkRuleCompilerTest {

    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void compilesDaowaiBillExportPriceRules() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("DAOWAI-RM")).isTrue();
        boolean hasPriceRule = false;
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("BILL_EXPORT_PRICE_RULE".equals(rule.path("ruleType").asText())) {
                hasPriceRule = true;
            }
        }
        assertThat(hasPriceRule).isTrue();
    }

    @Test
    void compilesEryyValidateOnlyWithoutExportDiscountPolicy() {
        ObjectNode compiled = compiler.compileForCustomer("ERYY-NG");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("ERYY-NG")).isTrue();
        boolean hasValidateOnly = false;
        for (JsonNode rule : compiled.path("clerkRules")) {
            if ("PRICE_VALIDATE_ONLY".equals(rule.path("ruleType").asText())) {
                hasValidateOnly = true;
            }
        }
        assertThat(hasValidateOnly).isTrue();
        for (JsonNode policy : compiled.path("billingPolicies")) {
            assertThat(policy.path("params").path("validateOnly").asBoolean(false)).isFalse();
        }
    }

    @Test
    void compilesJiuzhouSettlementDiscounts() {
        ObjectNode compiled = compiler.compileForCustomer("JIUZHOU-FK");
        assertThat(compiled).isNotNull();
        JsonNode policies = compiled.path("billingPolicies");
        assertThat(policies.isArray()).isTrue();
        assertThat(policies.size()).isGreaterThanOrEqualTo(2);
        boolean hasHt = false;
        boolean hasLt = false;
        for (JsonNode policy : policies) {
            String temp = policy.path("scope").path("temperature").asText("");
            if ("HT".equals(temp)) {
                hasHt = true;
                assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.5);
            }
            if ("LT".equals(temp)) {
                hasLt = true;
                assertThat(policy.path("params").path("rate").asDouble()).isEqualTo(0.7);
            }
        }
        assertThat(hasHt).isTrue();
        assertThat(hasLt).isTrue();
    }

    @Test
    void compilesTaipingPieceTierExportDiscount() {
        ObjectNode compiled = compiler.compileForCustomer("TAIPING-RM");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("TAIPING-RM")).isTrue();
        boolean hasPieceTier = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if (policy.path("params").path("pieceTierDiscounts").isArray()) {
                hasPieceTier = true;
            }
        }
        assertThat(hasPieceTier).isTrue();
    }

    @Test
    void compilesRenshengLogisticsCard() {
        ObjectNode compiled = compiler.compileForCustomer("RENSHENG");
        assertThat(compiled).isNotNull();
        boolean hasCard = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("LOGISTICS".equals(policy.path("policyType").asText())
                    && policy.path("params").path("useLogisticsCard").asBoolean()) {
                hasCard = true;
            }
        }
        assertThat(hasCard).isTrue();
    }

    @Test
    void compilesHulanTcmMinCharge() {
        ObjectNode compiled = compiler.compileForCustomer("HULAN-TCM");
        assertThat(compiled).isNotNull();
        boolean hasMinCharge = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if ("MONTHLY_SETTLEMENT".equals(policy.path("policyType").asText())) {
                hasMinCharge = true;
                assertThat(policy.path("params").path("minCharge").asDouble()).isEqualTo(10000);
            }
        }
        assertThat(hasMinCharge).isTrue();
    }

    @Test
    void compilesZyyD1FuyiClerkRules() {
        ObjectNode compiled = compiler.compileForCustomer("ZYY-D1");
        assertThat(compiled).isNotNull();
        assertThat(compiler.hasActiveBillExportRules("ZYY-D1")).isTrue();

        boolean hasFuyiLayout = false;
        for (JsonNode layout : compiled.path("exportLayouts")) {
            JsonNode params = layout.path("params");
            if ("fuyi_extended_11col".equals(params.path("billColumnLayout").asText())
                    && "dept_split".equals(params.path("billLayout").asText())) {
                hasFuyiLayout = true;
            }
        }
        assertThat(hasFuyiLayout).isTrue();

        boolean hasLogistics = false;
        boolean hasWashing = false;
        boolean hasUrgent = false;
        for (JsonNode policy : compiled.path("billingPolicies")) {
            String type = policy.path("policyType").asText();
            if ("LOGISTICS".equals(type)
                    && policy.path("params").path("feePerTrip").asDouble() == 45.0) {
                hasLogistics = true;
            }
            if ("SETTLEMENT_EXTRA".equals(type)
                    && "手术一区洗涤费用".equals(policy.path("params").path("itemName").asText())) {
                hasWashing = true;
            }
            if ("URGENT".equals(type)
                    && policy.path("params").path("baseMultiplier").asDouble() == 1.25) {
                hasUrgent = true;
            }
        }
        assertThat(hasLogistics).isTrue();
        assertThat(hasWashing).isTrue();
        assertThat(hasUrgent).isTrue();

        assertThat(compiled.path("exportSupplementTypes"))
                .extracting(JsonNode::asText)
                .contains("dept_summary", "logistics_allocation");
    }
}
