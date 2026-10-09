package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationPricingOrchestratorTest {

    private ReconciliationPricingOrchestrator orchestrator;
    private PricingEngine pricingEngine;

    @BeforeEach
    void setUp() {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        pricingEngine = new PricingEngine(JsonUtils.getObjectMapper().createObjectNode());
    }

    @Test
    void clerkPriceShortCircuitsPricingEngine() {
        ObjectNode clerkCompiled = clerkWithPriceRule("整形包", 8.0);
        Map<String, Object> row = baseRow("整形包", "高温蒸汽灭菌", 6);

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "AOLAN-YY", Set.of());

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.pricingRule).contains("内勤计价");
        assertThat(result.billingNotes.get("pricingLayer")).isEqualTo("clerk");
        assertThat(result.billingNotes.get("clerkRuleName")).isEqualTo("整形包");
    }

    @Test
    void fallsBackToPricingEngineWhenClerkMisses() {
        ObjectNode clerkCompiled = clerkWithPriceRule("其他包", 99.0);
        Map<String, Object> row = baseRow("整形包", "高温蒸汽灭菌", 6);

        PricingEngine spyEngine = new PricingEngine(JsonUtils.getObjectMapper().createObjectNode()) {
            @Override
            public ProcessedResult processRow(Map<String, Object> row) {
                ProcessedResult pr = new ProcessedResult();
                pr.expectedUnitPrice = 12.5;
                pr.pricingRule = "高温无纺布计费";
                pr.pricingPath = "standard";
                return pr;
            }
        };

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, spyEngine, clerkCompiled, "AOLAN-YY", Set.of());

        assertThat(result.expectedUnitPrice).isEqualTo(12.5);
        assertThat(result.billingNotes.get("pricingLayer")).isEqualTo("standard");
    }

    @Test
    void appliesClerkDiscountAfterBasePrice() {
        ObjectNode clerkCompiled = clerkWithPriceRule("整形包", 10.0);
        ArrayNode policies = clerkCompiled.putArray("billingPolicies");
        policies.add(discountPolicy("导出七五折", 0.75));

        Map<String, Object> row = baseRow("整形包", "高温蒸汽灭菌", 6);
        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, pricingEngine, clerkCompiled, "AOLAN-YY", Set.of());

        assertThat(result.expectedUnitPrice).isEqualTo(7.5);
        assertThat(result.billingNotes.get("clerkDiscountRuleName")).isEqualTo("导出七五折");
        assertThat(result.billingNotes.get("priceBeforeDiscount")).isEqualTo(10.0);
        assertThat(result.billingNotes.get("priceAfterDiscount")).isEqualTo(7.5);
        assertThat(result.correctedTotalPrice).isEqualTo(7.5);
        assertThat(result.status).isEqualTo("corrected");
    }

    @Test
    void disabledClerkPriceCategoryFallsBackToEngine() {
        ObjectNode clerkCompiled = clerkWithPriceRule("整形包", 8.0);
        Map<String, Object> row = baseRow("整形包", "高温蒸汽灭菌", 6);

        PricingEngine spyEngine = new PricingEngine(JsonUtils.getObjectMapper().createObjectNode()) {
            @Override
            public ProcessedResult processRow(Map<String, Object> row) {
                ProcessedResult pr = new ProcessedResult();
                pr.expectedUnitPrice = 16.0;
                pr.pricingRule = "特色固定价";
                pr.pricingPath = "fixed";
                pr.matchedRuleId = 1L;
                return pr;
            }
        };

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, spyEngine, clerkCompiled, "AOLAN-YY",
                Set.of(ReconciliationPricingOrchestrator.CATEGORY_CLERK_PRICE));

        assertThat(result.expectedUnitPrice).isEqualTo(16.0);
        assertThat(result.billingNotes.get("pricingLayer")).isEqualTo("special");
        assertThat(result.billingNotes.get("disabledClerkLayers")).asList().contains("内勤计价");
    }

    private static Map<String, Object> baseRow(String packName, String type, int instrumentCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("packName", packName);
        row.put("type", type);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", 1);
        row.put("unitPrice", 8.0);
        row.put("totalPrice", 8.0);
        return row;
    }

    private static ObjectNode clerkWithPriceRule(String packName, double price) {
        ObjectNode compiled = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode clerkRules = compiled.putArray("clerkRules");
        ObjectNode rule = JsonUtils.getObjectMapper().createObjectNode();
        rule.put("ruleType", "PACK_NAME_PRICE");
        rule.put("stage", "bill_export");
        rule.put("name", packName);
        rule.put("isActive", true);
        rule.put("priority", 10);
        ObjectNode params = rule.putObject("params");
        ArrayNode keywords = params.putArray("packNameKeywords");
        keywords.add(packName);
        params.put("unitPrice", price);
        params.put("unitPriceMode", "FIXED");
        clerkRules.add(rule);
        return compiled;
    }

    private static ObjectNode discountPolicy(String name, double rate) {
        ObjectNode policy = JsonUtils.getObjectMapper().createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", name);
        policy.put("priority", 10);
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("rate", rate);
        return policy;
    }
}
