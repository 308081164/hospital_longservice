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
        for (JsonNode policy : policies) {
            if ("LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
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
