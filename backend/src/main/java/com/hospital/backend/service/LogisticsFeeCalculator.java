package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按发货日期去重计趟次，并优先使用客户 LOGISTICS 策略的 feePerTrip 覆盖全局 logistics.feePerTrip。
 */
public final class LogisticsFeeCalculator {

    private LogisticsFeeCalculator() {
    }

    public record Result(
            int tripCount,
            double feePerTrip,
            double totalFee,
            String feeSource,
            Long policyId
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules, List<Map<String, Object>> rows) {
        JsonNode logisticsNode = compiledRules.path("logistics");
        if (!logisticsNode.path("enabled").asBoolean(false)) {
            return Optional.empty();
        }

        FeeResolution fee = resolveFeePerTrip(compiledRules);
        Set<String> uniqueDates = collectUniqueDeliveryDates(rows);
        int tripCount = uniqueDates.size();
        if (tripCount <= 0) {
            return Optional.empty();
        }
        double totalFee = Math.round(tripCount * fee.feePerTrip() * 100.0) / 100.0;
        return Optional.of(new Result(tripCount, fee.feePerTrip(), totalFee, fee.source(), fee.policyId()));
    }

    public static String toBreakdownJson(Result result) {
        return JsonUtils.toJson(toBreakdownMap(result));
    }

    public static Map<String, Object> toBreakdownMap(Result result) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("tripCount", result.tripCount());
        breakdown.put("feePerTrip", result.feePerTrip());
        breakdown.put("total", result.totalFee());
        breakdown.put("feeSource", result.feeSource());
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        return breakdown;
    }

    private record FeeResolution(double feePerTrip, String source, Long policyId) {
    }

    static FeeResolution resolveFeePerTrip(JsonNode compiledRules) {
        JsonNode overrides = compiledRules.path("customerOverrides");
        if (overrides.has("logisticsFeePerTrip") && !overrides.path("logisticsFeePerTrip").isNull()) {
            Long policyId = overrides.has("logisticsPolicyId")
                    ? overrides.path("logisticsPolicyId").asLong()
                    : null;
            return new FeeResolution(
                    overrides.path("logisticsFeePerTrip").asDouble(),
                    "customer",
                    policyId);
        }

        JsonNode policies = compiledRules.path("billingPolicies");
        if (policies.isArray()) {
            for (JsonNode policy : policies) {
                if (!"LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                    continue;
                }
                JsonNode feeNode = policy.path("params").path("feePerTrip");
                if (!feeNode.isMissingNode() && !feeNode.isNull()) {
                    Long policyId = policy.has("policyId") ? policy.path("policyId").asLong() : null;
                    return new FeeResolution(feeNode.asDouble(), "customer", policyId);
                }
            }
        }

        double globalFee = compiledRules.path("logistics").path("feePerTrip").asDouble(50.0);
        return new FeeResolution(globalFee, "global", null);
    }

    private static Set<String> collectUniqueDeliveryDates(List<Map<String, Object>> rows) {
        Set<String> uniqueDates = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object deliveryDate = row.get("deliveryDate");
            if (deliveryDate == null) {
                continue;
            }
            String dateStr = deliveryDate.toString().trim();
            if (dateStr.isBlank()) {
                continue;
            }
            uniqueDates.add(dateStr.split("\\s+")[0]);
        }
        return uniqueDates;
    }
}
