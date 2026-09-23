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
        assertThat(discount.note()).isEqualTo("九折优惠");
    }

    @Test
    void formatSettlementDiscountRemarkUsesSevenFoldLabel() {
        assertThat(BillingPolicyApplier.formatSettlementDiscountRemark("结款七折", 0.7))
                .isEqualTo("七折优惠");
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

    @Test
    void settlementOnlyPolicyDoesNotApplyToBillDetail() {
        ObjectNode rules = rulesWithDiscount("settlement_only", 0.9, "ANY");

        BillingPolicyApplier.BillDetailDiscount billDetail = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "工程大学", 100.0, 1, false, false);

        assertThat(billDetail).isNull();
    }

    @Test
    void multiApplyStagesMatchBillDetailAndSettlement() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "多范围折扣");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        ArrayNode stages = params.putArray("applyStages");
        stages.add("bill_detail");
        stages.add("settlement_only");
        params.put("rate", 0.8);
        params.put("skipWhenFixedPrice", false);

        BillingPolicyApplier.BillDetailDiscount billDetail = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "测试医院", 100.0, 1, false, false);
        BillingPolicyApplier.BillDetailDiscount settlement = BillingPolicyApplier.applySettlementDiscount(
                rules, "", "", "", "测试医院", 1000.0);

        assertThat(billDetail).isNotNull();
        assertThat(billDetail.price()).isEqualTo(80.0);
        assertThat(settlement).isNotNull();
        assertThat(settlement.price()).isEqualTo(800.0);
        assertThat(BillingPolicyApplier.findPoliciesByStage(rules, "DISCOUNT", BillingPolicyApplier.STAGE_EXPORT_ONLY))
                .isEmpty();
    }

    @Test
    void stackedBillDetailDiscountsRoundAtEachStep() {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode first = policies.addObject();
        first.put("policyType", "DISCOUNT");
        first.put("name", "附一对账八折");
        first.put("priority", 10);
        first.putObject("scope").put("temperature", "ANY");
        ObjectNode firstParams = first.putObject("params");
        firstParams.put("applyStage", "bill_detail");
        firstParams.put("rate", 0.8);
        firstParams.put("skipWhenFixedPrice", false);

        ObjectNode second = policies.addObject();
        second.put("policyType", "DISCOUNT");
        second.put("name", "附一对账九九折");
        second.put("priority", 20);
        second.putObject("scope").put("temperature", "ANY");
        ObjectNode secondParams = second.putObject("params");
        secondParams.put("applyStage", "bill_detail");
        secondParams.put("rate", 0.99);
        secondParams.put("skipWhenFixedPrice", false);

        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋 10cm", "中医附一", 8.0, 1, false, false);

        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(6.34);
        assertThat(discount.note()).contains("0.8").contains("0.99");
    }

    @Test
    void secondDiscountStepRoundsHalfUpExample() {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "附一对账九九折");
        policy.put("priority", 20);
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "bill_detail");
        params.put("rate", 0.99);
        params.put("skipWhenFixedPrice", false);

        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 6.4, 1, false, false);

        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(6.39);
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
