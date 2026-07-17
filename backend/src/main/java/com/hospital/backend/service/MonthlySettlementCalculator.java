package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按任务灭菌费合计应用客户 MONTHLY_SETTLEMENT 策略（低消 minCharge / 封顶 maxCap）。
 * P4-11：excludeCategories[] 不计入低消基数。
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
            String policyName,
            double excludedTotal
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules, double sterilizeTotal) {
        return compute(compiledRules, sterilizeTotal, List.of());
    }

    public static Optional<Result> compute(
            JsonNode compiledRules,
            double sterilizeTotal,
            List<Map<String, Object>> rows) {
        JsonNode policy = findMonthlyPolicy(compiledRules);
        if (policy == null) {
            return Optional.empty();
        }

        Double minCharge = readPositiveParam(policy, "minCharge");
        Double maxCap = readPositiveParam(policy, "maxCap");
        if (minCharge == null && maxCap == null) {
            return Optional.empty();
        }

        List<String> excludeCategories = parseExcludeCategories(policy);
        double excludedTotal = sumExcludedRows(rows, excludeCategories);
        double baseForMin = round2(sterilizeTotal - excludedTotal);

        double raw = round2(sterilizeTotal);
        double adjusted = baseForMin;
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
                policyName,
                excludedTotal
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
        if (result.excludedTotal() > 0) {
            breakdown.put("excludedTotal", result.excludedTotal());
        }
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        breakdown.put("policyName", result.policyName());
        return breakdown;
    }

    private static double sumExcludedRows(List<Map<String, Object>> rows, List<String> excludeCategories) {
        if (rows == null || rows.isEmpty() || excludeCategories.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map<String, Object> row : rows) {
            if (matchesExcludeCategory(row, excludeCategories)) {
                Object total = row.get("correctedTotalPrice");
                if (total == null) {
                    total = row.get("totalPrice");
                }
                if (total instanceof Number n) {
                    sum += n.doubleValue();
                }
            }
        }
        return round2(sum);
    }

    private static boolean matchesExcludeCategory(Map<String, Object> row, List<String> excludeCategories) {
        String packName = str(row, "packName");
        String categoryNo = str(row, "categoryNo");
        String type = str(row, "type");
        String combined = packName + categoryNo + type;
        for (String cat : excludeCategories) {
            if (cat != null && !cat.isBlank() && combined.contains(cat.trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseExcludeCategories(JsonNode policy) {
        List<String> categories = new ArrayList<>();
        JsonNode node = policy.path("params").path("excludeCategories");
        if (!node.isArray()) {
            return categories;
        }
        node.forEach(n -> categories.add(n.asText()));
        return categories;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
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
