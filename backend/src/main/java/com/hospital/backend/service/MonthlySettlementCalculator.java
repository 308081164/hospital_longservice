package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 按任务灭菌费合计应用客户 MONTHLY_SETTLEMENT 策略（低消 minCharge / 封顶 maxCap）。
 */
public final class MonthlySettlementCalculator {

    private MonthlySettlementCalculator() {
    }

    public record Result(
            double rawSterilizeTotal,
            double adjustedTotal,
            double adjustment,
            Double minCharge,
            Double maxCap,
            Long policyId,
            String policyName
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules, double sterilizeTotal) {
        JsonNode policy = findMonthlyPolicy(compiledRules);
        if (policy == null) {
            return Optional.empty();
        }

        Double minCharge = readPositiveParam(policy, "minCharge");
        Double maxCap = readPositiveParam(policy, "maxCap");
        if (minCharge == null && maxCap == null) {
            return Optional.empty();
        }

        double raw = round2(sterilizeTotal);
        double adjusted = raw;
        if (minCharge != null && adjusted < minCharge) {
            adjusted = minCharge;
        }
        if (maxCap != null && adjusted > maxCap) {
            adjusted = maxCap;
        }
        adjusted = round2(adjusted);

        Long policyId = policy.has("policyId") ? policy.path("policyId").asLong() : null;
        String policyName = policy.path("name").asText("月度结算");

        return Optional.of(new Result(
                raw,
                adjusted,
                round2(adjusted - raw),
                minCharge,
                maxCap,
                policyId,
                policyName
        ));
    }

    public static String toBreakdownJson(Result result) {
        return JsonUtils.toJson(toBreakdownMap(result));
    }

    public static Map<String, Object> toBreakdownMap(Result result) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("rawSterilizeTotal", result.rawSterilizeTotal());
        breakdown.put("adjustedTotal", result.adjustedTotal());
        breakdown.put("adjustment", result.adjustment());
        if (result.minCharge() != null) {
            breakdown.put("minCharge", result.minCharge());
        }
        if (result.maxCap() != null) {
            breakdown.put("maxCap", result.maxCap());
        }
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        breakdown.put("policyName", result.policyName());
        return breakdown;
    }

    private static JsonNode findMonthlyPolicy(JsonNode compiledRules) {
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

    private static Double readPositiveParam(JsonNode policy, String field) {
        JsonNode node = policy.path("params").path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value = node.asDouble(Double.NaN);
        if (Double.isNaN(value) || value <= 0) {
            return null;
        }
        return value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
