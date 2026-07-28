package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.LogisticsImport;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按发货日期或独立导入计趟次，并优先使用客户 LOGISTICS 策略的 feePerTrip 覆盖全局 logistics.feePerTrip。
 * Phase 5 扩展：独立导入、按星期计费。
 */
public final class LogisticsFeeCalculator {

    private LogisticsFeeCalculator() {
    }

    public record Result(
            int tripCount,
            double feePerTrip,
            double totalFee,
            String feeSource,
            Long policyId,
            String tripSource
    ) {
    }

    public record LogisticsPolicyParams(
            double feePerTrip,
            String tripSource,
            String allocationMode,
            List<Integer> billingWeekdays,
            List<String> excludeDepartments,
            boolean cardDeductionEnabled,
            String cardDeductMode,
            Double cardMonthlyCap,
            Long logisticsMergeGroupId,
            boolean mergeSameDay,
            Long singleOwnerCustomerId,
            Long policyId,
            String feeSource,
            int waivedTrips,
            Integer tripCountOverride,
            Double settlementLogisticsAmountOverride
    ) {
    }

    public static Optional<Result> compute(JsonNode compiledRules, List<Map<String, Object>> rows) {
        return compute(compiledRules, rows, List.of());
    }

    public static Optional<Result> compute(
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            List<LogisticsImport> imports) {
        JsonNode logisticsNode = compiledRules.path("logistics");
        boolean hasLogisticsPolicy = BillingPolicyInspector.findLogisticsPolicy(compiledRules) != null;
        if (!logisticsNode.path("enabled").asBoolean(false) && !hasLogisticsPolicy) {
            return Optional.empty();
        }

        LogisticsPolicyParams params = resolvePolicyParams(compiledRules);
        TripCountResult tripResult = countTrips(params, rows, imports);
        if (tripResult.tripCount() <= 0) {
            return Optional.empty();
        }

        int rawTripCount = tripResult.tripCount();
        if (params.tripCountOverride() != null && params.tripCountOverride() > 0) {
            rawTripCount = params.tripCountOverride();
        }
        int waivedTrips = Math.min(params.waivedTrips(), rawTripCount);
        int billableTrips = rawTripCount - waivedTrips;

        double totalFee;
        if (params.settlementLogisticsAmountOverride() != null
                && params.settlementLogisticsAmountOverride() > 0) {
            totalFee = roundCurrency(params.settlementLogisticsAmountOverride());
        } else if (tripResult.fixedFeeTotal() != null && tripResult.fixedFeeTotal() > 0) {
            totalFee = roundCurrency(tripResult.fixedFeeTotal());
        } else {
            totalFee = roundCurrency(billableTrips * params.feePerTrip());
        }

        return Optional.of(new Result(
                rawTripCount,
                params.feePerTrip(),
                totalFee,
                params.feeSource(),
                params.policyId(),
                params.tripSource()));
    }

    public static LogisticsPolicyParams resolvePolicyParams(JsonNode compiledRules) {
        FeeResolution fee = resolveFeePerTrip(compiledRules);
        JsonNode policyParams = findLogisticsPolicyParams(compiledRules);

        String tripSource = policyParams.path("tripSource").asText("delivery_date");
        String allocationMode = policyParams.path("allocationMode").asText("none");
        List<Integer> billingWeekdays = parseWeekdays(policyParams.path("billingWeekdays"));
        List<String> excludeDepartments = parseStringList(policyParams.path("excludeDepartments"));
        String cardDeductMode = policyParams.path("cardDeductMode").asText("auto");
        Double cardMonthlyCap = policyParams.has("cardMonthlyCap") && !policyParams.path("cardMonthlyCap").isNull()
                ? policyParams.path("cardMonthlyCap").asDouble()
                : null;
        Long logisticsMergeGroupId = policyParams.has("logisticsMergeGroupId")
                && !policyParams.path("logisticsMergeGroupId").isNull()
                ? policyParams.path("logisticsMergeGroupId").asLong()
                : null;
        boolean mergeSameDay = !policyParams.has("mergeSameDay")
                || policyParams.path("mergeSameDay").asBoolean(true);
        boolean useLogisticsCard = policyParams.path("useLogisticsCard").asBoolean(false);
        boolean cardDeductionEnabled = useLogisticsCard
                || !policyParams.has("cardDeductionEnabled")
                || policyParams.path("cardDeductionEnabled").asBoolean(true);
        Long singleOwnerCustomerId = policyParams.has("singleOwnerCustomerId")
                && !policyParams.path("singleOwnerCustomerId").isNull()
                ? policyParams.path("singleOwnerCustomerId").asLong()
                : null;
        int waivedTrips = policyParams.has("waivedTrips") && !policyParams.path("waivedTrips").isNull()
                ? Math.max(0, policyParams.path("waivedTrips").asInt())
                : 0;
        Integer tripCountOverride = policyParams.has("tripCountOverride")
                && !policyParams.path("tripCountOverride").isNull()
                ? policyParams.path("tripCountOverride").asInt()
                : null;
        Double settlementLogisticsAmountOverride =
                policyParams.has("settlementLogisticsAmountOverride")
                        && !policyParams.path("settlementLogisticsAmountOverride").isNull()
                        ? policyParams.path("settlementLogisticsAmountOverride").asDouble()
                        : null;

        return new LogisticsPolicyParams(
                fee.feePerTrip(),
                tripSource,
                allocationMode,
                billingWeekdays,
                excludeDepartments,
                cardDeductionEnabled,
                cardDeductMode,
                cardMonthlyCap,
                logisticsMergeGroupId,
                mergeSameDay,
                singleOwnerCustomerId,
                fee.policyId(),
                fee.source(),
                waivedTrips,
                tripCountOverride,
                settlementLogisticsAmountOverride);
    }

    public static String toBreakdownJson(Result result) {
        return JsonUtils.toJson(toBreakdownMap(result));
    }

    public static Map<String, Object> toBreakdownMap(Result result) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("tripCount", result.tripCount());
        breakdown.put("feePerTrip", result.feePerTrip());
        breakdown.put("total", result.totalFee());
        breakdown.put("payableFee", result.totalFee());
        breakdown.put("feeSource", result.feeSource());
        breakdown.put("tripSource", result.tripSource());
        if (result.policyId() != null) {
            breakdown.put("policyId", result.policyId());
        }
        return breakdown;
    }

    public static Map<String, Object> toBreakdownMap(Result result, int waivedTrips) {
        Map<String, Object> breakdown = toBreakdownMap(result);
        if (waivedTrips > 0) {
            breakdown.put("waivedTrips", Math.min(waivedTrips, result.tripCount()));
        }
        return breakdown;
    }

    private record TripCountResult(int tripCount, Double fixedFeeTotal) {
    }

    private record FeeResolution(double feePerTrip, String source, Long policyId) {
    }

    static TripCountResult countTrips(
            LogisticsPolicyParams params,
            List<Map<String, Object>> rows,
            List<LogisticsImport> imports) {
        if ("import".equalsIgnoreCase(params.tripSource()) && imports != null && !imports.isEmpty()) {
            int tripCount = 0;
            double fixedTotal = 0;
            boolean hasFixed = false;
            Set<LocalDate> uniqueDates = new LinkedHashSet<>();
            for (LogisticsImport item : imports) {
                if (item.getTripDate() == null || !matchesWeekday(item.getTripDate(), params.billingWeekdays())) {
                    continue;
                }
                uniqueDates.add(item.getTripDate());
                int count = item.getTripCount() != null && item.getTripCount() > 0 ? item.getTripCount() : 1;
                tripCount += count;
                if (item.getFeeAmount() != null && item.getFeeAmount() > 0) {
                    fixedTotal += item.getFeeAmount();
                    hasFixed = true;
                }
            }
            if (tripCount <= 0 && !uniqueDates.isEmpty()) {
                tripCount = uniqueDates.size();
            }
            return new TripCountResult(tripCount, hasFixed ? fixedTotal : null);
        }

        Set<String> uniqueDates = collectUniqueDeliveryDates(rows, params.billingWeekdays());
        if (!params.mergeSameDay()) {
            int perRowTrips = countDeliveryRowTrips(rows, params.billingWeekdays());
            if (perRowTrips > 0) {
                return new TripCountResult(perRowTrips, null);
            }
        }
        return new TripCountResult(uniqueDates.size(), null);
    }

    private static int countDeliveryRowTrips(
            List<Map<String, Object>> rows,
            List<Integer> billingWeekdays) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object deliveryDate = row.get("deliveryDate");
            if (deliveryDate == null) {
                deliveryDate = row.get("delivery_date");
            }
            if (deliveryDate == null) {
                continue;
            }
            String dateStr = deliveryDate.toString().trim();
            if (dateStr.isBlank()) {
                continue;
            }
            LocalDate parsed = parseDate(dateStr.split("\\s+")[0]);
            if (parsed != null && matchesWeekday(parsed, billingWeekdays)) {
                count++;
            }
        }
        return count;
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
            JsonNode bestPolicy = null;
            int bestPriority = Integer.MIN_VALUE;
            for (JsonNode policy : policies) {
                if (!"LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                    continue;
                }
                int priority = policy.path("priority").asInt(0);
                if (bestPolicy == null || priority >= bestPriority) {
                    bestPolicy = policy;
                    bestPriority = priority;
                }
            }
            if (bestPolicy != null) {
                JsonNode feeNode = bestPolicy.path("params").path("feePerTrip");
                if (!feeNode.isMissingNode() && !feeNode.isNull()) {
                    Long policyId = bestPolicy.has("policyId") ? bestPolicy.path("policyId").asLong() : null;
                    return new FeeResolution(feeNode.asDouble(), "customer", policyId);
                }
            }
        }

        double globalFee = compiledRules.path("logistics").path("feePerTrip").asDouble(50.0);
        return new FeeResolution(globalFee, "global", null);
    }

    private static JsonNode findLogisticsPolicyParams(JsonNode compiledRules) {
        JsonNode policies = compiledRules.path("billingPolicies");
        if (policies.isArray()) {
            JsonNode bestParams = null;
            int bestPriority = Integer.MIN_VALUE;
            for (JsonNode policy : policies) {
                if (!"LOGISTICS".equalsIgnoreCase(policy.path("policyType").asText())) {
                    continue;
                }
                int priority = policy.path("priority").asInt(0);
                if (bestParams == null || priority >= bestPriority) {
                    bestParams = policy.path("params");
                    bestPriority = priority;
                }
            }
            if (bestParams != null) {
                return bestParams;
            }
        }
        return compiledRules.path("logistics");
    }

    private static Set<String> collectUniqueDeliveryDates(
            List<Map<String, Object>> rows,
            List<Integer> billingWeekdays) {
        Set<String> uniqueDates = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object deliveryDate = row.get("deliveryDate");
            if (deliveryDate == null) {
                deliveryDate = row.get("delivery_date");
            }
            if (deliveryDate == null) {
                continue;
            }
            String dateStr = deliveryDate.toString().trim();
            if (dateStr.isBlank()) {
                continue;
            }
            String dayPart = dateStr.split("\\s+")[0];
            LocalDate parsed = parseDate(dayPart);
            if (parsed != null && !matchesWeekday(parsed, billingWeekdays)) {
                continue;
            }
            uniqueDates.add(dayPart);
        }
        return uniqueDates;
    }

    static boolean matchesWeekday(LocalDate date, List<Integer> billingWeekdays) {
        if (billingWeekdays == null || billingWeekdays.isEmpty()) {
            return true;
        }
        int dayValue = date.getDayOfWeek().getValue();
        return billingWeekdays.contains(dayValue);
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static List<Integer> parseWeekdays(JsonNode node) {
        List<Integer> weekdays = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return weekdays;
        }
        for (JsonNode item : node) {
            if (item.isInt()) {
                weekdays.add(item.asInt());
            }
        }
        return weekdays;
    }

    private static List<String> parseStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    public static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** 用于测试：ISO 星期值 1=Mon … 7=Sun */
    static int weekdayValue(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }
}
