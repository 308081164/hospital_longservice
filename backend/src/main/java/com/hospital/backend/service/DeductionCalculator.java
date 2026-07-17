package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 固定月减设备抵扣（FR-M9-04 / 新发红十字 -3270 元）。
 */
public final class DeductionCalculator {

    private DeductionCalculator() {
    }

    public record Result(
            double monthlyAmount,
            Long policyId,
            String policyName
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules) {
        JsonNode policy = findDeductionPolicy(compiledRules);
        if (policy == null) {
            return Optional.empty();
        }

        JsonNode amountNode = policy.path("params").path("monthlyAmount");
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            return Optional.empty();
        }
        double monthlyAmount = amountNode.asDouble(Double.NaN);
        if (Double.isNaN(monthlyAmount) || monthlyAmount <= 0) {
            return Optional.empty();
        }

        Long policyId = policy.has("policyId") ? policy.path("policyId").asLong() : null;
        String policyName = policy.path("name").asText("设备抵扣");

        return Optional.of(new Result(round2(monthlyAmount), policyId, policyName));
    }

    public static String toBreakdownJson(Result result) {
        return JsonUtils.toJson(toBreakdownMap(result));
    }

    public static Map<String, Object> toBreakdownMap(Result result) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("monthlyAmount", result.monthlyAmount());
        breakdown.put("deductionAmount", -result.monthlyAmount());
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        breakdown.put("policyName", result.policyName());
        return breakdown;
    }

    private static JsonNode findDeductionPolicy(JsonNode compiledRules) {
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if ("DEDUCTION".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
