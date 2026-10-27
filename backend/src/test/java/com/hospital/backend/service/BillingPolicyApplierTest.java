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
        assertThat(BillingPolicyApplier.formatSettlementDiscountRemark("结款八折", 0.8))
                .isEqualTo("8折优惠");
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
        // billingPieces=单包器械数（PricingEngine.materialBillingCount），与 Excel「器械数量」对齐
        ObjectNode rules = fuyiPieceConditionalDiscountRules();

        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋 10cm", "中医附一", 8.0, 1, false, false);

        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(6.00);
        assertThat(discount.note()).contains("0.75");
        assertThat(discount.note()).doesNotContain("0.999");
    }

    @Test
    void fuyiElevenYuanBelowThreePiecesStacksToEightPointSevenNine() {
        // 器械数 1 或 2（<3）：11×0.75 → 8.25；billingPieces=单包件数
        ObjectNode rules = fuyiPieceConditionalDiscountRules();

        BillingPolicyApplier.BillDetailDiscount onePiece = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 11.0, 1, false, false);
        BillingPolicyApplier.BillDetailDiscount twoPieces = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 11.0, 2, false, false);

        assertThat(onePiece).isNotNull();
        assertThat(onePiece.price()).isEqualTo(8.25);
        assertThat(onePiece.note()).contains("0.75").contains("8.25");
        assertThat(onePiece.note()).doesNotContain("0.999");
        assertThat(twoPieces).isNotNull();
        assertThat(twoPieces.price()).isEqualTo(8.25);
    }

    @Test
    void fuyiElevenYuanAtLeastThreePiecesOnlyEightFold() {
        // 器械数 >=3：11×0.8 → 8.80，不再乘 0.999；billingPieces=单包件数
        ObjectNode rules = fuyiPieceConditionalDiscountRules();

        BillingPolicyApplier.BillDetailDiscount threePieces = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 11.0, 3, false, false);
        BillingPolicyApplier.BillDetailDiscount fourPieces = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 11.0, 4, false, false);

        assertThat(threePieces).isNotNull();
        assertThat(threePieces.price()).isEqualTo(8.80);
        assertThat(threePieces.note()).contains("0.8").contains("8.80");
        assertThat(threePieces.note()).doesNotContain("0.999");
        assertThat(fourPieces).isNotNull();
        assertThat(fourPieces.price()).isEqualTo(8.80);
    }

    @Test
    void secondDiscountStepRoundsHalfUpExample() {
        // 仅九九九折分段：验证 6.4×0.999 四舍五入到分 = 6.39（不叠八折）
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "附一九九九折·件数<3");
        policy.put("priority", 20);
        ObjectNode scope = policy.putObject("scope");
        scope.put("temperature", "ANY");
        scope.putArray("acceptedTypes").add("器械包");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "bill_detail");
        params.put("skipWhenFixedPrice", false);
        ObjectNode tier = params.putArray("pieceTierDiscounts").addObject();
        tier.put("minPieces", 1);
        tier.put("maxPieces", 2);
        tier.put("rate", 0.999);
        tier.put("decimalPlaces", 2);

        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包", "测试", "纸塑袋", "中医附一", 6.4, 1, false, false);

        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(6.39);
    }

    @Test
    void fuyiNonWovenExtraBelowThreeUsesFixedThirteenPointTwo() {
        ObjectNode rules = fuyiTypedDiscountRules();
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "额外包（无纺布）", "测试", "无纺布", "中医附一", 20.0, 2, false, false);
        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(13.2);
        assertThat(discount.note()).contains("固定单价");
        assertThat(discount.note()).doesNotContain("0.8");
    }

    @Test
    void fuyiNonWovenExtraAtLeastThreeOnlyEightFold() {
        ObjectNode rules = fuyiTypedDiscountRules();
        BillingPolicyApplier.BillDetailDiscount discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "额外包(无纺布)", "测试", "无纺布", "中医附一", 20.0, 3, false, false);
        assertThat(discount).isNotNull();
        assertThat(discount.price()).isEqualTo(16.0);
        assertThat(discount.note()).contains("0.8");
        assertThat(discount.note()).doesNotContain("0.999");
    }

    @Test
    void fuyiDressingPaperAnyPiecesOnlyEightFold() {
        ObjectNode rules = fuyiTypedDiscountRules();
        BillingPolicyApplier.BillDetailDiscount one = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "敷料包（纸塑袋）", "测试", "纸塑袋", "中医附一", 11.0, 1, false, false);
        BillingPolicyApplier.BillDetailDiscount three = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "敷料包(纸塑袋)", "测试", "纸塑袋", "中医附一", 11.0, 3, false, false);
        assertThat(one.price()).isEqualTo(8.80);
        assertThat(one.note()).doesNotContain("0.999");
        assertThat(three.price()).isEqualTo(8.80);
        assertThat(three.note()).doesNotContain("0.999");
    }

    @Test
    void fuyiLowTempAnyPiecesStacksEightAndNineNineNine() {
        ObjectNode rules = fuyiTypedDiscountRules();
        BillingPolicyApplier.BillDetailDiscount plasma = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "单包装包（等离子）", "测试", "低温纸塑袋", "中医附一", 11.0, 5, false, false);
        BillingPolicyApplier.BillDetailDiscount eto = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "额外包（ETO）", "测试", "低温纸塑袋", "中医附一", 11.0, 5, false, false);
        BillingPolicyApplier.BillDetailDiscount dressingNw = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "敷料包（无纺布）", "测试", "无纺布", "中医附一", 11.0, 5, false, false);
        assertThat(plasma.price()).isEqualTo(8.79);
        assertThat(eto.price()).isEqualTo(8.79);
        assertThat(dressingNw.price()).isEqualTo(10.99);
        assertThat(dressingNw.note()).contains("0.999");
        assertThat(dressingNw.note()).doesNotContain("0.8");
    }

    @Test
    void fuyiPaperExtraOneAndThreePieceTiers() {
        ObjectNode rules = fuyiTypedDiscountRules();
        BillingPolicyApplier.BillDetailDiscount one = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "额外包（纸塑袋）", "测试", "纸塑袋", "中医附一", 11.0, 1, false, false);
        BillingPolicyApplier.BillDetailDiscount three = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "额外包(纸塑袋)", "测试", "纸塑袋", "中医附一", 11.0, 3, false, false);
        assertThat(one.price()).isEqualTo(8.25);
        assertThat(one.note()).contains("0.75");
        assertThat(three.price()).isEqualTo(8.80);
        assertThat(three.note()).doesNotContain("0.999");
        assertThat(three.note()).doesNotContain("0.75");
    }

    /** 与旧版全局叠折兼容的精简样例（无类型门控）。 */
    private ObjectNode fuyiPieceConditionalDiscountRules() {
        return fuyiTypedDiscountRules();
    }

    /** 与 ZYY-D1 baseline 2026-10-22 一致：按包类型门控。 */
    private ObjectNode fuyiTypedDiscountRules() {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");

        ObjectNode fixed = policies.addObject();
        fixed.put("policyType", "DISCOUNT");
        fixed.put("name", "附一额外无纺布<3固定价");
        fixed.put("priority", 5);
        ObjectNode fixedScope = fixed.putObject("scope");
        fixedScope.put("temperature", "ANY");
        fixedScope.putArray("acceptedTypes").add("额外包（无纺布）");
        ObjectNode fixedParams = fixed.putObject("params");
        fixedParams.put("applyStage", "bill_detail");
        fixedParams.put("skipWhenFixedPrice", false);
        ObjectNode fixedTier = fixedParams.putArray("pieceTierDiscounts").addObject();
        fixedTier.put("minPieces", 1);
        fixedTier.put("maxPieces", 2);
        fixedTier.put("fixedUnitPrice", 13.2);
        fixedTier.put("decimalPlaces", 2);

        ObjectNode eight = policies.addObject();
        eight.put("policyType", "DISCOUNT");
        eight.put("name", "附一对账八折");
        eight.put("priority", 10);
        ObjectNode eightScope = eight.putObject("scope");
        eightScope.put("temperature", "ANY");
        ArrayNode eightTypes = eightScope.putArray("acceptedTypes");
        for (String t : new String[] {
                "额外包（无纺布）", "敷料包（纸塑袋）", "单包装包（等离子）", "单包装包（EO）",
                "额外包（低温等离子）", "额外包（ETO）"
        }) {
            eightTypes.add(t);
        }
        ObjectNode eightParams = eight.putObject("params");
        eightParams.put("applyStage", "bill_detail");
        eightParams.put("rate", 0.8);
        eightParams.put("skipWhenFixedPrice", false);

        ObjectNode seventyFive = policies.addObject();
        seventyFive.put("policyType", "DISCOUNT");
        seventyFive.put("name", "附一＜3件系统价七五折");
        seventyFive.put("priority", 20);
        ObjectNode seventyFiveScope = seventyFive.putObject("scope");
        seventyFiveScope.put("temperature", "ANY");
        ArrayNode seventyFiveTypes = seventyFiveScope.putArray("acceptedTypes");
        for (String t : new String[] {"额外包（纸塑袋）", "器械包（ZSD）", "器械包", "单包装包"}) {
            seventyFiveTypes.add(t);
        }
        ObjectNode seventyFiveParams = seventyFive.putObject("params");
        seventyFiveParams.put("applyStage", "bill_detail");
        seventyFiveParams.put("skipWhenFixedPrice", false);
        ObjectNode lowTier = seventyFiveParams.putArray("pieceTierDiscounts").addObject();
        lowTier.put("minPieces", 1);
        lowTier.put("maxPieces", 2);
        lowTier.put("rate", 0.75);
        lowTier.put("decimalPlaces", 2);

        ObjectNode eightHigh = policies.addObject();
        eightHigh.put("policyType", "DISCOUNT");
        eightHigh.put("name", "附一对账八折·件数≥3");
        eightHigh.put("priority", 10);
        ObjectNode eightHighScope = eightHigh.putObject("scope");
        eightHighScope.put("temperature", "ANY");
        ArrayNode eightHighTypes = eightHighScope.putArray("acceptedTypes");
        for (String t : new String[] {"额外包（纸塑袋）", "器械包（ZSD）", "器械包", "单包装包"}) {
            eightHighTypes.add(t);
        }
        ObjectNode eightHighParams = eightHigh.putObject("params");
        eightHighParams.put("applyStage", "bill_detail");
        eightHighParams.put("skipWhenFixedPrice", false);
        ObjectNode highTier = eightHighParams.putArray("pieceTierDiscounts").addObject();
        highTier.put("minPieces", 3);
        highTier.put("rate", 0.8);
        highTier.put("decimalPlaces", 2);

        ObjectNode nineAny = policies.addObject();
        nineAny.put("policyType", "DISCOUNT");
        nineAny.put("name", "附一九九九折·件数无关");
        nineAny.put("priority", 20);
        ObjectNode nineAnyScope = nineAny.putObject("scope");
        nineAnyScope.put("temperature", "ANY");
        ArrayNode nineAnyTypes = nineAnyScope.putArray("acceptedTypes");
        for (String t : new String[] {
                "单包装包（等离子）", "单包装包（EO）", "额外包（低温等离子）", "额外包（ETO）"
        }) {
            nineAnyTypes.add(t);
        }
        ObjectNode nineAnyParams = nineAny.putObject("params");
        nineAnyParams.put("applyStage", "bill_detail");
        nineAnyParams.put("rate", 0.999);
        nineAnyParams.put("skipWhenFixedPrice", false);

        ObjectNode dressing = policies.addObject();
        dressing.put("policyType", "DISCOUNT");
        dressing.put("name", "附一敷料无纺布系统价九九九折");
        dressing.put("priority", 20);
        ObjectNode dressingScope = dressing.putObject("scope");
        dressingScope.put("temperature", "ANY");
        dressingScope.putArray("acceptedTypes").add("敷料包（无纺布）");
        ObjectNode dressingParams = dressing.putObject("params");
        dressingParams.put("applyStage", "bill_detail");
        dressingParams.put("rate", 0.999);
        dressingParams.put("skipWhenFixedPrice", false);
        return rules;
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
