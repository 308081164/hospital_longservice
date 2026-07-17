package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPolicyApplierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void billDetailStageSkipsExportOnlyPolicy() throws Exception {
        ObjectNode rules = rulesWithDiscount("export_only", 0.75, "ANY");
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "太平人民",
                100.0, 2, false, false);
        assertThat(discount).isNull();
    }

    @Test
    void settlementOnlyPolicyAppliesAtSettlementStage() throws Exception {
        ObjectNode rules = rulesWithDiscount("settlement_only", 0.9, "ANY");
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applySettlementDiscount(
                rules, "", "", "", "工程大学", 10000.0);
        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(9000.0);
    }

    @Test
    void pieceTierDiscountAppliesByInstrumentCount() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "分段折扣");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "bill_detail");
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier1 = tiers.addObject();
        tier1.put("minPieces", 1);
        tier1.put("maxPieces", 1);
        tier1.put("rate", 1.0);
        tier1.put("decimalPlaces", 1);
        ObjectNode tier2 = tiers.addObject();
        tier2.put("minPieces", 2);
        tier2.put("rate", 0.75);
        tier2.put("decimalPlaces", 2);

        BillingPolicyApplier.BillDetailDiscount onePiece = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "太平人民", 16.5, 1, false, false);
        BillingPolicyApplier.BillDetailDiscount twoPieces = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "太平人民", 20.0, 2, false, false);

        assertThat(onePiece.price()).isEqualTo(16.5);
        assertThat(twoPieces.price()).isEqualTo(15.0);
    }

    private ObjectNode rulesWithDiscount(String applyStage, double rate, String temperature) {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "测试折扣");
        policy.putObject("scope").put("temperature", temperature);
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", applyStage);
        params.put("rate", rate);
        params.put("skipWhenFixedPrice", false);
        return rules;
    }
}
