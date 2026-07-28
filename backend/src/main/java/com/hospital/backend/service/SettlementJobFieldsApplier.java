package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账 finalize 与结款函 export 共用的 Job 结算字段填充（物流管线 / 加急 / 低消）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementJobFieldsApplier {

    private static final List<String> HULAN_TCM_SETTLEMENT_PACK_KEYWORDS = List.of(
            "外科包", "阑尾包");

    private final LogisticsPipelineService logisticsPipelineService;
    private final CustomerResolver customerResolver;

    public void applyAll(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows,
            boolean persistCardDeduction) {
        applyAllFromMaps(job, compiledRules, toRowMaps(rows), persistCardDeduction);
    }

    public void applyAllFromMaps(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<Map<String, Object>> rowMaps,
            boolean persistCardDeduction) {
        if (job == null || compiledRules == null) {
            return;
        }
        applyLogistics(job, compiledRules, rowMaps, persistCardDeduction);
        applyUrgentAndDeduction(job, compiledRules, rowMaps);
        applyMonthlySettlement(job, compiledRules, rowMaps);
    }

    public void applyLogistics(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            boolean persistCardDeduction) {
        try {
            if (BillingPolicyInspector.hasLogisticsPolicy(compiledRules)) {
                Long customerId = customerResolver.resolveByName(job.getHospitalName())
                        .map(c -> c.getId())
                        .orElse(null);
                String billingMonth = BillingMonthResolver.resolve(job);
                Map<String, Object> breakdown = logisticsPipelineService.buildBreakdownForJob(
                        customerId,
                        job.getId(),
                        billingMonth,
                        compiledRules,
                        rows,
                        persistCardDeduction);
                if (!breakdown.isEmpty()) {
                    applyLogisticsBreakdownToJob(job, breakdown);
                    return;
                }
            }
            LogisticsFeeCalculator.compute(compiledRules, rows).ifPresentOrElse(
                    result -> {
                        job.setLogisticsTripCount(result.tripCount());
                        job.setLogisticsFee(result.totalFee());
                        job.setLogisticsBreakdown(LogisticsFeeCalculator.toBreakdownJson(result));
                    },
                    () -> {
                        job.setLogisticsFee(null);
                        job.setLogisticsTripCount(null);
                        job.setLogisticsBreakdown(null);
                    });
        } catch (Exception e) {
            log.warn("物流费计算失败 job={}: {}", job.getId(), e.getMessage());
        }
    }

    private void applyLogisticsBreakdownToJob(HospitalReconciliationJob job, Map<String, Object> breakdown) {
        Object tripCount = breakdown.get("tripCount");
        Object payable = breakdown.getOrDefault("payableFee", breakdown.get("total"));
        if (tripCount instanceof Number number) {
            job.setLogisticsTripCount(number.intValue());
        }
        if (payable instanceof Number feeNumber) {
            job.setLogisticsFee(feeNumber.doubleValue());
        }
        job.setLogisticsBreakdown(logisticsPipelineService.toBreakdownJson(breakdown));
    }

    public void applyUrgentAndDeduction(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<Map<String, Object>> rows) {
        if (!applyUrgentBreakdownFromPolicy(job, compiledRules)) {
            UrgentFeeCalculator.compute(compiledRules, rows).ifPresentOrElse(
                    result -> job.setUrgentBreakdown(UrgentFeeCalculator.toBreakdownJson(result)),
                    () -> job.setUrgentBreakdown(null));
        }
        DeductionCalculator.compute(compiledRules).ifPresentOrElse(
                result -> job.setDeductionBreakdown(DeductionCalculator.toBreakdownJson(result)),
                () -> job.setDeductionBreakdown(null));
    }

    public void applyMonthlySettlement(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<Map<String, Object>> rows) {
        if (BillingPolicyInspector.settlementOmitMinChargeRow(compiledRules)) {
            job.setSettlementAdjustment(null);
            job.setMonthlyBreakdown(null);
            return;
        }
        double sterilizeTotal = job.getCorrectedTotalPrice() != null ? job.getCorrectedTotalPrice() : 0.0;
        double monthlyBase = computeMonthlySettlementBase(job, compiledRules, rows, sterilizeTotal);
        MonthlySettlementCalculator.compute(compiledRules, monthlyBase, rows).ifPresentOrElse(
                result -> {
                    job.setSettlementAdjustment(result.adjustment());
                    job.setMonthlyBreakdown(MonthlySettlementCalculator.toBreakdownJson(result));
                },
                () -> {
                    job.setSettlementAdjustment(null);
                    job.setMonthlyBreakdown(null);
                });
    }

    private double computeMonthlySettlementBase(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            double sterilizeTotal) {
        double base = sterilizeTotal;
        if (job.getLogisticsFee() != null) {
            base += job.getLogisticsFee();
        }
        if (isHulanTcmHospital(job.getHospitalName())) {
            base += sumSpecialPackTotalsFromMaps(rows);
        }
        return round2(base);
    }

    private static boolean isHulanTcmHospital(String hospitalName) {
        return hospitalName != null && hospitalName.contains("呼兰") && hospitalName.contains("中医");
    }

    private static double sumSpecialPackTotalsFromMaps(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map<String, Object> row : rows) {
            String packName = str(row, "packName").trim();
            for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
                if (!packName.equals(keyword) && !packName.contains(keyword)) {
                    continue;
                }
                Object total = row.get("correctedTotalPrice");
                if (total == null) {
                    total = row.get("totalPrice");
                }
                if (total instanceof Number n) {
                    sum += n.doubleValue();
                }
            }
        }
        return sum;
    }

    private List<Map<String, Object>> toRowMaps(List<HospitalReconciliationRow> rows) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (rows == null) {
            return maps;
        }
        for (HospitalReconciliationRow row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", row.getType());
            map.put("packName", row.getPackName());
            map.put("packageMaterial", row.getPackageMaterial());
            map.put("categoryNo", row.getCategoryNo());
            map.put("totalPrice", row.getTotalPrice());
            map.put("correctedTotalPrice", row.getCorrectedTotalPrice());
            map.put("deliveryDate", row.getDeliveryDate());
            map.put("isUrgent", row.getIsUrgent());
            map.put("is_urgent", row.getIsUrgent());
            map.put("sheetName", row.getSheetName());
            map.put("billingNotes", row.getBillingNotes());
            maps.add(map);
        }
        return maps;
    }

    private boolean applyUrgentBreakdownFromPolicy(HospitalReconciliationJob job, JsonNode compiledRules) {
        JsonNode policy = findUrgentPolicy(compiledRules);
        if (policy == null) {
            return false;
        }
        String billingMonth = BillingMonthResolver.resolve(job);
        JsonNode byMonth = policy.path("params").path("urgentBreakdownByMonth");
        if (billingMonth == null || !byMonth.has(billingMonth)) {
            return false;
        }
        JsonNode preset = byMonth.path(billingMonth);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        preset.fields().forEachRemaining(entry ->
                breakdown.put(entry.getKey(), entry.getValue().isNumber()
                        ? entry.getValue().asDouble()
                        : entry.getValue().asText()));
        if (!breakdown.containsKey("urgentRowCount")) {
            breakdown.put("urgentRowCount", 1);
        }
        job.setUrgentBreakdown(com.hospital.backend.common.JsonUtils.toJson(breakdown));
        return true;
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

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
