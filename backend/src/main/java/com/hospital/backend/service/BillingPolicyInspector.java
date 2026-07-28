package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 读取 compiledRules 中的 billingPolicies 辅助判定。
 */
public final class BillingPolicyInspector {

    private BillingPolicyInspector() {
    }

    public static boolean hasLogisticsPolicy(JsonNode compiledRules) {
        return findLogisticsPolicy(compiledRules) != null;
    }

    public static JsonNode findLogisticsPolicy(JsonNode compiledRules) {
        if (compiledRules == null) {
            return null;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        JsonNode best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (JsonNode policy : policies) {
            if (!"LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            int priority = policy.path("priority").asInt(0);
            if (best == null || priority >= bestPriority) {
                best = policy;
                bestPriority = priority;
            }
        }
        return best;
    }

    public static double resolveLogisticsFeePerTrip(JsonNode compiledRules) {
        JsonNode policy = findLogisticsPolicy(compiledRules);
        if (policy != null) {
            JsonNode feeNode = policy.path("params").path("feePerTrip");
            if (!feeNode.isMissingNode() && !feeNode.isNull()) {
                return feeNode.asDouble();
            }
        }
        return compiledRules != null
                ? compiledRules.path("logistics").path("feePerTrip").asDouble(50.0)
                : 50.0;
    }

    public static boolean settlementOmitMinChargeRow(JsonNode compiledRules) {
        JsonNode policy = findMonthlyPolicy(compiledRules);
        if (policy == null) {
            return false;
        }
        JsonNode params = policy.path("params");
        if (params.path("settlementOmitMinChargeRow").asBoolean(false)) {
            return true;
        }
        return "embed".equalsIgnoreCase(params.path("settlementDisplayMode").asText(""));
    }

    public static boolean settlementOmitZeroRows(JsonNode compiledRules) {
        JsonNode policy = findLogisticsPolicy(compiledRules);
        if (policy != null && policy.path("params").path("settlementOmitZeroRows").asBoolean(false)) {
            return true;
        }
        return compiledRules != null
                && compiledRules.path("settlement").path("omitZeroRows").asBoolean(false);
    }

    public static boolean settlementOmitLogisticsRow(JsonNode compiledRules) {
        JsonNode policy = findLogisticsPolicy(compiledRules);
        return policy != null
                && policy.path("params").path("settlementOmitLogisticsRow").asBoolean(false);
    }

    public static String resolveUrgentLineMode(JsonNode compiledRules) {
        JsonNode policy = findUrgentPolicy(compiledRules);
        if (policy == null) {
            return "surcharge";
        }
        return policy.path("params").path("urgentLineMode").asText("surcharge");
    }

    public static OptionalSettlementExtra resolveSettlementExtra(JsonNode compiledRules, String billingMonth) {
        JsonNode policy = findSettlementExtraPolicy(compiledRules);
        if (policy == null) {
            return null;
        }
        String itemName = policy.path("params").path("itemName").asText("");
        if (itemName.isBlank()) {
            return null;
        }
        JsonNode byMonth = policy.path("params").path("amountByMonth");
        if (billingMonth != null && byMonth.has(billingMonth)) {
            return new OptionalSettlementExtra(itemName, byMonth.path(billingMonth).asDouble(0));
        }
        if (policy.path("params").has("amount")) {
            return new OptionalSettlementExtra(itemName, policy.path("params").path("amount").asDouble(0));
        }
        return null;
    }

    public record OptionalSettlementExtra(String itemName, double amount) {
    }

    public record SettlementOverride(
            Double sterilizeAmount,
            Double externalInstrumentAmount,
            Double logisticsAmount,
            Double xinfaSystemSterilize,
            Double xinfaHtDiscounted,
            Double xinfaDressing,
            Double minChargeAdjustment
    ) {
    }

    public static SettlementOverride resolveSettlementOverride(JsonNode compiledRules, String billingMonth) {
        JsonNode policy = findSettlementOverridePolicy(compiledRules);
        if (policy == null) {
            return null;
        }
        JsonNode params = policy.path("params");
        Double sterilize = readAmountByMonth(params, "sterilizeAmount", "sterilizeAmountByMonth", billingMonth);
        Double external = readAmountByMonth(params, "externalInstrumentAmount",
                "externalInstrumentAmountByMonth", billingMonth);
        Double logistics = readAmountByMonth(params, "logisticsAmount", "logisticsAmountByMonth", billingMonth);
        Double xinfaSystem = readAmountByMonth(params, "xinfaSystemSterilizeAmount",
                "xinfaSystemSterilizeAmountByMonth", billingMonth);
        Double xinfaHt = readAmountByMonth(params, "xinfaHtDiscountedAmount",
                "xinfaHtDiscountedAmountByMonth", billingMonth);
        Double xinfaDressing = readAmountByMonth(params, "xinfaDressingAmount",
                "xinfaDressingAmountByMonth", billingMonth);
        Double minCharge = readAmountByMonth(params, "minChargeAdjustment", "minChargeAdjustmentByMonth", billingMonth);
        if (sterilize == null && external == null && logistics == null
                && xinfaSystem == null && xinfaHt == null && xinfaDressing == null && minCharge == null) {
            return null;
        }
        return new SettlementOverride(sterilize, external, logistics, xinfaSystem, xinfaHt, xinfaDressing, minCharge);
    }

    private static Double readAmountByMonth(
            JsonNode params, String amountField, String byMonthField, String billingMonth) {
        JsonNode byMonth = params.path(byMonthField);
        if (billingMonth != null && byMonth.has(billingMonth)) {
            return byMonth.path(billingMonth).asDouble();
        }
        if (params.has(amountField) && !params.path(amountField).isNull()) {
            return params.path(amountField).asDouble();
        }
        return null;
    }

    private static JsonNode findSettlementOverridePolicy(JsonNode compiledRules) {
        if (compiledRules == null) {
            return null;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if ("SETTLEMENT_OVERRIDE".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
    }

    private static JsonNode findUrgentPolicy(JsonNode compiledRules) {
        if (compiledRules == null) {
            return null;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        JsonNode fallback = null;
        for (JsonNode policy : policies) {
            if (!"URGENT".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            if (fallback == null) {
                fallback = policy;
            }
            if (policy.path("params").has("urgentBreakdownByMonth")) {
                return policy;
            }
        }
        return fallback;
    }

    private static JsonNode findSettlementExtraPolicy(JsonNode compiledRules) {
        if (compiledRules == null) {
            return null;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if ("SETTLEMENT_EXTRA".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
    }

    private static JsonNode findMonthlyPolicy(JsonNode compiledRules) {
        if (compiledRules == null) {
            return null;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if ("MONTHLY_SETTLEMENT".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
    }
}
