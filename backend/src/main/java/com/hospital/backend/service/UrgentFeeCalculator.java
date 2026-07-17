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
 * 加急灭菌费与加急物流费计算（FR-M9-02 / FR-M9-03）。
 * 默认：灭菌费 125%，减免后 102.5%；加急物流 150 元/趟，减免后 9 折。
 */
public final class UrgentFeeCalculator {

    private static final double DEFAULT_BASE_MULTIPLIER = 1.25;
    private static final double DEFAULT_ADJUSTED_MULTIPLIER = 1.025;
    private static final double DEFAULT_URGENT_LOGISTICS_FEE = 150.0;
    private static final double DEFAULT_URGENT_LOGISTICS_DISCOUNT = 0.9;

    private UrgentFeeCalculator() {
    }

    public record Result(
            double urgentBaseTotal,
            int urgentRowCount,
            double baseMultiplier,
            double adjustedMultiplier,
            double nominalUrgentTotal,
            double adjustedUrgentTotal,
            double nominalSurcharge,
            double adjustedSurcharge,
            int urgentTripCount,
            double urgentLogisticsFeePerTrip,
            double urgentLogisticsDiscountRate,
            double nominalUrgentLogisticsTotal,
            double adjustedUrgentLogisticsTotal,
            Long policyId,
            String policyName
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules, List<Map<String, Object>> rows) {
        JsonNode policy = findUrgentPolicy(compiledRules);
        if (policy == null) {
            return Optional.empty();
        }

        double baseMultiplier = readParam(policy, "baseMultiplier", DEFAULT_BASE_MULTIPLIER);
        double adjustedMultiplier = readParam(policy, "adjustedMultiplier", DEFAULT_ADJUSTED_MULTIPLIER);
        double urgentLogisticsFeePerTrip = readParam(policy, "urgentLogisticsFeePerTrip", DEFAULT_URGENT_LOGISTICS_FEE);
        double urgentLogisticsDiscountRate = readParam(policy, "urgentLogisticsDiscountRate", DEFAULT_URGENT_LOGISTICS_DISCOUNT);

        double urgentBaseTotal = 0.0;
        int urgentRowCount = 0;
        Set<String> urgentDates = new LinkedHashSet<>();

        for (Map<String, Object> row : rows) {
            if (!isUrgentRow(row)) {
                continue;
            }
            urgentRowCount++;
            Double corrected = readDouble(row, "correctedTotalPrice");
            if (corrected == null) {
                corrected = readDouble(row, "totalPrice");
            }
            if (corrected != null) {
                urgentBaseTotal += corrected;
            }
            Object deliveryDate = row.get("deliveryDate");
            if (deliveryDate != null) {
                String dateStr = deliveryDate.toString().trim();
                if (!dateStr.isBlank()) {
                    urgentDates.add(dateStr.split("\\s+")[0]);
                }
            }
        }

        if (urgentRowCount <= 0) {
            return Optional.empty();
        }

        urgentBaseTotal = round2(urgentBaseTotal);
        double nominalUrgentTotal = round2(urgentBaseTotal * baseMultiplier);
        double adjustedUrgentTotal = round2(urgentBaseTotal * adjustedMultiplier);
        double nominalSurcharge = round2(nominalUrgentTotal - urgentBaseTotal);
        double adjustedSurcharge = round2(adjustedUrgentTotal - urgentBaseTotal);

        int urgentTripCount = urgentDates.size();
        double nominalUrgentLogisticsTotal = round2(urgentTripCount * urgentLogisticsFeePerTrip);
        double adjustedUrgentLogisticsTotal = round2(nominalUrgentLogisticsTotal * urgentLogisticsDiscountRate);

        Long policyId = policy.has("policyId") ? policy.path("policyId").asLong() : null;
        String policyName = policy.path("name").asText("加急收费");

        return Optional.of(new Result(
                urgentBaseTotal,
                urgentRowCount,
                baseMultiplier,
                adjustedMultiplier,
                nominalUrgentTotal,
                adjustedUrgentTotal,
                nominalSurcharge,
                adjustedSurcharge,
                urgentTripCount,
                urgentLogisticsFeePerTrip,
                urgentLogisticsDiscountRate,
                nominalUrgentLogisticsTotal,
                adjustedUrgentLogisticsTotal,
                policyId,
                policyName
        ));
    }

    public static String toBreakdownJson(Result result) {
        return JsonUtils.toJson(toBreakdownMap(result));
    }

    public static Map<String, Object> toBreakdownMap(Result result) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("urgentBaseTotal", result.urgentBaseTotal());
        breakdown.put("urgentRowCount", result.urgentRowCount());
        breakdown.put("baseMultiplier", result.baseMultiplier());
        breakdown.put("adjustedMultiplier", result.adjustedMultiplier());
        breakdown.put("nominalUrgentTotal", result.nominalUrgentTotal());
        breakdown.put("adjustedUrgentTotal", result.adjustedUrgentTotal());
        breakdown.put("nominalSurcharge", result.nominalSurcharge());
        breakdown.put("adjustedSurcharge", result.adjustedSurcharge());
        breakdown.put("urgentTripCount", result.urgentTripCount());
        breakdown.put("urgentLogisticsFeePerTrip", result.urgentLogisticsFeePerTrip());
        breakdown.put("urgentLogisticsDiscountRate", result.urgentLogisticsDiscountRate());
        breakdown.put("nominalUrgentLogisticsTotal", result.nominalUrgentLogisticsTotal());
        breakdown.put("adjustedUrgentLogisticsTotal", result.adjustedUrgentLogisticsTotal());
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        breakdown.put("policyName", result.policyName());
        return breakdown;
    }

    static boolean isUrgentRow(Map<String, Object> row) {
        Object flag = row.get("isUrgent");
        if (flag == null) {
            flag = row.get("is_urgent");
        }
        if (flag instanceof Boolean bool) {
            return bool;
        }
        if (flag instanceof Number number) {
            return number.intValue() != 0;
        }
        if (flag instanceof String str) {
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        }
        return false;
    }

    private static JsonNode findUrgentPolicy(JsonNode compiledRules) {
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if ("URGENT".equalsIgnoreCase(policy.path("policyType").asText())) {
                return policy;
            }
        }
        return null;
    }

    private static double readParam(JsonNode policy, String field, double defaultValue) {
        JsonNode node = policy.path("params").path(field);
        if (node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        double value = node.asDouble(Double.NaN);
        return Double.isNaN(value) ? defaultValue : value;
    }

    private static Double readDouble(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
