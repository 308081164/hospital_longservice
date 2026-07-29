package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 策略层折扣应用（TD-02 / P4-02）：分温折扣、applyStage、按把数分段折扣。
 */
public final class BillingPolicyApplier {

    public static final String STAGE_BILL_DETAIL = "bill_detail";
    public static final String STAGE_SETTLEMENT_ONLY = "settlement_only";
    public static final String STAGE_EXPORT_ONLY = "export_only";

    private BillingPolicyApplier() {
    }

    /** 试算器用：返回折扣率 */
    public record AppliedDiscount(
            double rate,
            String label,
            boolean skipWhenFixedPrice,
            Long policyId,
            List<String> trace
    ) {}

    /** 账单明细用：返回折后单价 */
    public record BillDetailDiscount(
            double price,
            String ruleSuffix,
            String note,
            Long policyId
    ) {}

    public record PieceTierDiscount(
            int minPieces,
            Integer maxPieces,
            double rate,
            int decimalPlaces,
            Double fixedUnitPrice,
            Double originalUnitPriceEquals
    ) {}

    public static AppliedDiscount resolveBestDiscount(
            JsonNode rules,
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            boolean skipHospitalDiscount) {
        List<String> trace = new ArrayList<>();
        if (skipHospitalDiscount) {
            trace.add("规则标记 skipHospitalDiscount，跳过客户折扣");
            return new AppliedDiscount(1.0, null, true, null, trace);
        }

        JsonNode billingPolicies = rules.path("billingPolicies");
        if (billingPolicies.isArray()) {
            String rowTemp = BillingConditionEvaluator.resolveRowTemperature(type + packName + packageMaterial);
            JsonNode matched = findScopedDiscountPolicy(billingPolicies, rowTemp, false, STAGE_BILL_DETAIL);
            if (matched != null) {
                double rate = matched.path("params").path("rate").asDouble(1.0);
                String label = matched.path("name").asText("客户折扣");
                boolean skipFixed = matched.path("params").path("skipWhenFixedPrice").asBoolean(true);
                Long policyId = matched.has("policyId") ? matched.path("policyId").asLong() : null;
                trace.add("命中策略 " + label + " rate=" + rate);
                return new AppliedDiscount(rate, label, skipFixed, policyId, trace);
            }
        }

        JsonNode overrides = rules.path("customerOverrides");
        if (overrides.has("discountRate")) {
            double rate = overrides.path("discountRate").asDouble(1.0);
            String label = overrides.path("discountLabel").asText("客户折扣");
            boolean skipFixed = overrides.path("skipWhenFixedPrice").asBoolean(true);
            trace.add("命中 customerOverrides 折扣 rate=" + rate);
            return new AppliedDiscount(rate, label, skipFixed, null, trace);
        }

        trace.add("未命中折扣策略");
        return new AppliedDiscount(1.0, null, true, null, trace);
    }

    public static BillDetailDiscount applyBillDetailDiscounts(
            JsonNode rules,
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            double baseUnitPrice,
            int billingPieces,
            boolean skipHospitalDiscount,
            boolean hitFixedPrice) {
        if (skipHospitalDiscount) {
            return null;
        }
        return buildBillDetailDiscount(rules, type, packName, packageMaterial, hospitalName,
                baseUnitPrice, billingPieces, hitFixedPrice, STAGE_BILL_DETAIL);
    }

    public static BillDetailDiscount applySettlementDiscount(
            JsonNode rules,
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            double baseAmount) {
        return buildBillDetailDiscount(rules, type, packName, packageMaterial, hospitalName,
                baseAmount, 1, false, STAGE_SETTLEMENT_ONLY);
    }

    private static BillDetailDiscount buildBillDetailDiscount(
            JsonNode rules,
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            double baseUnitPrice,
            int billingPieces,
            boolean hitFixedPrice,
            String targetStage) {
        JsonNode billingPolicies = rules.path("billingPolicies");
        if (billingPolicies.isArray() && !billingPolicies.isEmpty()) {
            String rowTemp = BillingConditionEvaluator.resolveRowTemperature(
                    type + packName + packageMaterial);
            JsonNode matched = findScopedDiscountPolicy(billingPolicies, rowTemp, hitFixedPrice, targetStage);
            if (matched != null) {
                return toBillDetailDiscount(matched, hospitalName, baseUnitPrice, billingPieces);
            }
            if (STAGE_BILL_DETAIL.equals(targetStage)) {
                return null;
            }
        }

        if (!STAGE_BILL_DETAIL.equals(targetStage)) {
            return null;
        }
        JsonNode customerOverrides = rules.path("customerOverrides");
        if (!customerOverrides.has("discountRate")) {
            return null;
        }
        if (hitFixedPrice && customerOverrides.path("skipWhenFixedPrice").asBoolean(true)) {
            return null;
        }
        double rate = customerOverrides.path("discountRate").asDouble(1.0);
        if (rate <= 0 || rate >= 1.0) {
            return null;
        }
        double price = round(baseUnitPrice * rate);
        String label = customerOverrides.path("discountLabel").asText("客户折扣");
        String displayName = customerOverrides.path("displayName").asText(hospitalName);
        return new BillDetailDiscount(
                price,
                " + " + displayName + " " + rate + "倍计费",
                "命中" + label + "，基础规则单价 "
                        + fmt(baseUnitPrice) + " 元 × " + rate + " = " + fmt(price) + " 元。",
                null
        );
    }

    public static List<JsonNode> findPoliciesByStage(JsonNode rules, String policyType, String targetStage) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode policies = rules.path("billingPolicies");
        if (!policies.isArray()) {
            return result;
        }
        for (JsonNode policy : policies) {
            if (!policyType.equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            if (stageMatches(policy, targetStage)) {
                result.add(policy);
            }
        }
        result.sort(Comparator.comparingInt(p -> p.path("priority").asInt(100)));
        return result;
    }

    public static double applyPieceTierRate(double basePrice, int billingPieces, List<PieceTierDiscount> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return basePrice;
        }
        for (PieceTierDiscount tier : tiers) {
            if (billingPieces < tier.minPieces()) {
                continue;
            }
            if (tier.maxPieces() != null && billingPieces > tier.maxPieces()) {
                continue;
            }
            if (tier.originalUnitPriceEquals() != null
                    && Math.abs(basePrice - tier.originalUnitPriceEquals()) > 0.001) {
                continue;
            }
            if (tier.fixedUnitPrice() != null) {
                return roundToPlaces(tier.fixedUnitPrice(), tier.decimalPlaces());
            }
            return roundToPlaces(basePrice * tier.rate(), tier.decimalPlaces());
        }
        return basePrice;
    }

    public static List<PieceTierDiscount> parsePieceTierDiscounts(JsonNode params) {
        List<PieceTierDiscount> tiers = new ArrayList<>();
        JsonNode node = params.path("pieceTierDiscounts");
        if (!node.isArray()) {
            return tiers;
        }
        for (JsonNode tier : node) {
            int minPieces = tier.path("minPieces").asInt(1);
            Integer maxPieces = tier.has("maxPieces") && !tier.path("maxPieces").isNull()
                    ? tier.path("maxPieces").asInt()
                    : null;
            double rate = tier.path("rate").asDouble(1.0);
            int decimalPlaces = tier.path("decimalPlaces").asInt(2);
            Double fixedUnitPrice = tier.has("fixedUnitPrice") && !tier.path("fixedUnitPrice").isNull()
                    ? tier.path("fixedUnitPrice").asDouble()
                    : null;
            Double originalUnitPriceEquals = tier.has("originalUnitPriceEquals")
                    && !tier.path("originalUnitPriceEquals").isNull()
                    ? tier.path("originalUnitPriceEquals").asDouble()
                    : null;
            tiers.add(new PieceTierDiscount(
                    minPieces, maxPieces, rate, decimalPlaces, fixedUnitPrice, originalUnitPriceEquals));
        }
        tiers.sort(Comparator.comparingInt(PieceTierDiscount::minPieces));
        return tiers;
    }

    private static JsonNode findScopedDiscountPolicy(
            JsonNode policies,
            String rowTemp,
            boolean hitFixedPrice,
            String targetStage) {
        List<JsonNode> discounts = new ArrayList<>();
        for (JsonNode policy : policies) {
            if ("DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())
                    && stageMatches(policy, targetStage)) {
                discounts.add(policy);
            }
        }
        discounts.sort(Comparator.comparingInt(p -> p.path("priority").asInt(100)));
        JsonNode anyFallback = null;
        for (JsonNode policy : discounts) {
            String scopeTemp = policy.path("scope").path("temperature").asText("ANY");
            if (!BillingConditionEvaluator.temperatureScopeMatches(scopeTemp, rowTemp)) {
                continue;
            }
            if (hitFixedPrice && policy.path("params").path("skipWhenFixedPrice").asBoolean(true)) {
                continue;
            }
            if ("ANY".equalsIgnoreCase(scopeTemp)) {
                anyFallback = policy;
                continue;
            }
            return policy;
        }
        return anyFallback;
    }

    static boolean stageMatches(JsonNode policy, String targetStage) {
        JsonNode params = policy.path("params");
        JsonNode stagesNode = params.path("applyStages");
        if (stagesNode.isArray() && !stagesNode.isEmpty()) {
            for (JsonNode stageNode : stagesNode) {
                if (stageMatchesSingle(stageNode.asText(""), targetStage)) {
                    return true;
                }
            }
            return false;
        }
        return stageMatchesSingle(params.path("applyStage").asText(STAGE_BILL_DETAIL), targetStage);
    }

    private static boolean stageMatchesSingle(String applyStage, String targetStage) {
        if (applyStage == null || applyStage.isBlank()) {
            applyStage = STAGE_BILL_DETAIL;
        }
        return applyStage.equalsIgnoreCase(targetStage)
                || (STAGE_BILL_DETAIL.equals(targetStage)
                && ("after_base".equalsIgnoreCase(applyStage) || "bill_detail".equalsIgnoreCase(applyStage)));
    }

    private static BillDetailDiscount toBillDetailDiscount(
            JsonNode policy,
            String hospitalName,
            double baseUnitPrice,
            int billingPieces) {
        JsonNode params = policy.path("params");
        List<PieceTierDiscount> tiers = parsePieceTierDiscounts(params);
        double price;
        String tierNote = "";
        if (!tiers.isEmpty()) {
            price = applyPieceTierRate(baseUnitPrice, billingPieces, tiers);
            tierNote = "（按 " + billingPieces + " 把分段折扣）";
        } else {
            double rate = params.path("rate").asDouble(1.0);
            if (rate <= 0 || rate >= 1.0) {
                return null;
            }
            price = round(baseUnitPrice * rate);
        }
        String label = policy.path("name").asText("客户折扣");
        String tempScope = policy.path("scope").path("temperature").asText("ANY");
        String tempNote = "ANY".equalsIgnoreCase(tempScope) ? "" : "（" + tempScope + "）";
        Long policyId = policy.has("policyId") ? policy.path("policyId").asLong() : null;
        return new BillDetailDiscount(
                price,
                " + " + hospitalName + " 折扣",
                "命中" + label + tempNote + tierNote + "，基础规则单价 "
                        + fmt(baseUnitPrice) + " 元 → " + fmt(price) + " 元。",
                policyId
        );
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static double roundToPlaces(double value, int places) {
        double factor = Math.pow(10, Math.max(0, places));
        return Math.round(value * factor) / factor;
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
