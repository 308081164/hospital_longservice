package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkRuleCompilerTest {

    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void compilesTaipingExportTierDiscount() {
        ObjectNode compiled = compiler.compileForCustomer("TAIPING-RM");
        assertThat(compiled).isNotNull();
        JsonNode policies = compiled.path("billingPolicies");
        assertThat(policies.isArray()).isTrue();
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).path("params").path("applyStage").asText())
                .isEqualTo(BillingPolicyApplier.STAGE_EXPORT_ONLY);
        assertThat(policies.get(0).path("params").path("pieceTierDiscounts").isArray()).isTrue();
    }

    @Test
    void skipsInactiveHulanDiscountUntilMigration() {
        ObjectNode compiled = compiler.compileForCustomer("HULAN-RM");
        assertThat(compiled).isNotNull();
        assertThat(compiled.path("billingPolicies").isArray()).isFalse();
        assertThat(compiler.hasActiveBillExportRules("HULAN-RM")).isFalse();
    }

    @Test
    void compilesSettlementDiscountForHeu() {
        ObjectNode compiled = compiler.compileForCustomer("HRB-HEU");
        assertThat(compiled).isNotNull();
        JsonNode policies = compiled.path("billingPolicies");
        assertThat(policies.get(0).path("params").path("applyStage").asText())
                .isEqualTo(BillingPolicyApplier.STAGE_SETTLEMENT_ONLY);
        assertThat(policies.get(0).path("params").path("rate").asDouble()).isEqualTo(0.9);
    }
}
