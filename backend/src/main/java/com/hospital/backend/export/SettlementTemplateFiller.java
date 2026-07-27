package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.BillingPolicyApplier;
import com.hospital.backend.service.BillingPolicyInspector;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds settlement fee rows from job settlement fields (monthly breakdown, logistics).
 * P4-10：结款函灭菌费独立打折（settlement_only 策略，不影响 row expected）。
 * 分温客户（维多利亚/九洲）：按 HT/LT 拆「高温灭菌费用」「低温灭菌费用」行。
 * 呼兰中医：外科包/阑尾包等独立结款行。
 */
@Slf4j
@Component
public class SettlementTemplateFiller {

    private static final List<String> HULAN_TCM_SETTLEMENT_PACK_KEYWORDS = List.of(
            "外科包", "阑尾包");

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public List<SettlementFeeRow> buildFeeRows(HospitalReconciliationJob job, double sterilizeTotal) {
        return buildFeeRows(job, sterilizeTotal, null, List.of());
    }

    public List<SettlementFeeRow> buildFeeRows(
            HospitalReconciliationJob job,
            double sterilizeTotal,
            JsonNode compiledRules) {
        return buildFeeRows(job, sterilizeTotal, compiledRules, List.of());
    }

    public List<SettlementFeeRow> buildFeeRows(
            HospitalReconciliationJob job,
            double sterilizeTotal,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows) {
        List<SettlementFeeRow> rowsOut = new ArrayList<>();
        int seq = 1;
        String hospitalName = job.getHospitalName();
        double baseSterilize = resolveBaseSterilizeTotal(sterilizeTotal, rows, hospitalName);

        if (shouldSplitSterilizeByTemperature(compiledRules, rows, hospitalName)) {
            seq = appendTemperatureSterilizeRows(rowsOut, seq, compiledRules, rows);
        } else {
            double displaySterilize = baseSterilize;
            String sterilizeRemark = "";
            if (compiledRules != null) {
                BillingPolicyApplier.BillDetailDiscount settlementDiscount =
                        BillingPolicyApplier.applySettlementDiscount(
                                compiledRules, "", "", "", hospitalName, baseSterilize);
                if (settlementDiscount != null) {
                    displaySterilize = settlementDiscount.price();
                    sterilizeRemark = settlementDiscount.note();
                }
            }
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(seq++)
                    .itemName(sterilizeItemLabel(hospitalName))
                    .amount(displaySterilize)
                    .remark(sterilizeRemark)
                    .build());
        }

        if (shouldShowLogisticsRow(job, compiledRules)) {
            Map<String, Object> logisticsBreakdown = parseLogisticsBreakdown(job.getLogisticsBreakdown());
            double cardDeducted = logisticsBreakdown != null && logisticsBreakdown.get("cardDeducted") instanceof Number n
                    ? n.doubleValue() : 0;
            double payableFee = logisticsBreakdown != null && logisticsBreakdown.get("payableFee") instanceof Number p
                    ? p.doubleValue() : (job.getLogisticsFee() != null ? job.getLogisticsFee() : 0);
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(seq++)
                    .itemName(logisticsItemLabel(job.getHospitalName()))
                    .amount(payableFee)
                    .remark(buildLogisticsRemark(job, cardDeducted, logisticsBreakdown))
                    .build());
            if (cardDeducted > 0) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(seq++)
                        .itemName("物流卡抵扣")
                        .amount(-cardDeducted)
                        .remark("卡内扣减")
                        .build());
            }
        }

        appendSpecialPackSettlementRows(rowsOut, rows, job.getHospitalName());

        parseUrgentBreakdown(job).ifPresent(urgent -> {
            if (Math.abs(urgent.nominalSurcharge()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急灭菌费")
                        .amount(urgent.nominalSurcharge())
                        .remark(String.format("%.0f%%", urgent.baseMultiplier() * 100))
                        .build());
            }
            if (Math.abs(urgent.adjustedSurcharge()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急灭菌费(减免后)")
                        .amount(urgent.adjustedSurcharge())
                        .remark(String.format("%.1f%%", urgent.adjustedMultiplier() * 100))
                        .build());
            }
            if (Math.abs(urgent.nominalUrgentLogisticsTotal()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急物流费")
                        .amount(urgent.nominalUrgentLogisticsTotal())
                        .remark(urgent.urgentTripCount() + " 趟 × "
                                + formatAmount(urgent.urgentLogisticsFeePerTrip()))
                        .build());
            }
            if (Math.abs(urgent.adjustedUrgentLogisticsTotal()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急物流费(减免后)")
                        .amount(urgent.adjustedUrgentLogisticsTotal())
                        .remark(String.format("%.0f%%", urgent.urgentLogisticsDiscountRate() * 100))
                        .build());
            }
        });

        if (!BillingPolicyInspector.settlementOmitMinChargeRow(compiledRules)) {
            parseMonthlyBreakdown(job).ifPresent(breakdown -> {
                if (breakdown.adjustment() != null && Math.abs(breakdown.adjustment()) >= 0.01) {
                    String label = breakdown.adjustment() > 0 ? "低消补差" : "封顶调减";
                    rowsOut.add(SettlementFeeRow.builder()
                            .sequence(rowsOut.size() + 1)
                            .itemName(label)
                            .amount(breakdown.adjustment())
                            .remark(Optional.ofNullable(breakdown.minCharge())
                                    .map(m -> "低消 " + m).orElse(""))
                            .build());
                }
            });
        }

        parseDeductionBreakdown(job).ifPresent(deduction -> {
            if (Math.abs(deduction.deductionAmount()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName(deduction.policyName() != null ? deduction.policyName() : "设备抵扣")
                        .amount(deduction.deductionAmount())
                        .remark("月度固定减免")
                        .build());
            }
        });

        parseExternalInstrumentTotal(job).ifPresent(externalTotal -> {
            if (Math.abs(externalTotal) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("外来器械")
                        .amount(externalTotal)
                        .remark("科室借调/外来器械汇总")
                        .build());
            }
        });

        resequence(rowsOut);
        return rowsOut;
    }

    private static String sterilizeItemLabel(String hospitalName) {
        return "灭菌费用";
    }

    private static String logisticsItemLabel(String hospitalName) {
        return "物流费用";
    }

    private double resolveBaseSterilizeTotal(
            double sterilizeTotal,
            List<HospitalReconciliationRow> rows,
            String hospitalName) {
        if (rows == null || rows.isEmpty() || !isHulanTcmHospital(hospitalName)) {
            return sterilizeTotal;
        }
        double specialPackTotal = sumSpecialPackTotals(rows);
        if (specialPackTotal <= 0) {
            return sterilizeTotal;
        }
        return round2(Math.max(0, sterilizeTotal - specialPackTotal));
    }

    private static boolean isHulanTcmHospital(String hospitalName) {
        return hospitalName != null && hospitalName.contains("呼兰") && hospitalName.contains("中医");
    }

    private double sumSpecialPackTotals(List<HospitalReconciliationRow> rows) {
        double sum = 0;
        for (HospitalReconciliationRow row : rows) {
            String packName = str(row.getPackName()).trim();
            for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
                if (packName.equals(keyword)) {
                    Double total = row.getCorrectedTotalPrice() != null
                            ? row.getCorrectedTotalPrice()
                            : row.getTotalPrice();
                    if (total != null) {
                        sum += total;
                    }
                }
            }
        }
        return sum;
    }

    private boolean shouldSplitSterilizeByTemperature(
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows,
            String hospitalName) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        if (hospitalName != null && (hospitalName.contains("维多利亚") || hospitalName.contains("九洲"))) {
            return true;
        }
        if (compiledRules == null) {
            return false;
        }
        boolean hasHt = false;
        boolean hasLt = false;
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return false;
        }
        for (JsonNode policy : policies) {
            if (!"DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            String temp = policy.path("scope").path("temperature").asText("ANY");
            if ("HT".equalsIgnoreCase(temp)) {
                hasHt = true;
            }
            if ("LT".equalsIgnoreCase(temp)) {
                hasLt = true;
            }
        }
        return hasHt && hasLt;
    }

    private int appendTemperatureSterilizeRows(
            List<SettlementFeeRow> rowsOut,
            int seq,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows) {
        double htRaw = sumRowsByTemperature(rows, "HT");
        double ltRaw = sumRowsByTemperature(rows, "LT");
        double htTotal = applyTemperatureDiscount(compiledRules, "HT", htRaw, 0.5);
        double ltTotal = applyTemperatureDiscount(compiledRules, "LT", ltRaw, 0.7);
        rowsOut.add(SettlementFeeRow.builder()
                .sequence(seq++)
                .itemName("高温灭菌费用")
                .amount(htTotal)
                .remark(temperaturePolicyRemark(compiledRules, "HT", "高温5折优惠"))
                .build());
        rowsOut.add(SettlementFeeRow.builder()
                .sequence(seq++)
                .itemName("低温灭菌费用")
                .amount(ltTotal)
                .remark(temperaturePolicyRemark(compiledRules, "LT", "低温7折优惠"))
                .build());
        if (!BillingPolicyInspector.settlementOmitZeroRows(compiledRules)) {
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(seq++)
                    .itemName("敷料")
                    .amount(0)
                    .remark("")
                    .build());
        }
        return seq;
    }

    private boolean shouldShowLogisticsRow(HospitalReconciliationJob job, JsonNode compiledRules) {
        Map<String, Object> logisticsBreakdown = parseLogisticsBreakdown(job.getLogisticsBreakdown());
        double payableFee = logisticsBreakdown != null && logisticsBreakdown.get("payableFee") instanceof Number p
                ? p.doubleValue()
                : (job.getLogisticsFee() != null ? job.getLogisticsFee() : 0);

        if (BillingPolicyInspector.hasLogisticsPolicy(compiledRules)) {
            double feePerTrip = BillingPolicyInspector.resolveLogisticsFeePerTrip(compiledRules);
            if (feePerTrip <= 0) {
                return false;
            }
            return job.getLogisticsFee() != null || payableFee != 0 || logisticsBreakdown != null;
        }
        return job.getLogisticsFee() != null && Math.abs(payableFee) >= 0.01;
    }

    private double applyTemperatureDiscount(
            JsonNode compiledRules,
            String temperature,
            double rawAmount,
            double fallbackRate) {
        if (compiledRules != null) {
            JsonNode policies = compiledRules.path("billingPolicies");
            if (policies.isArray()) {
                for (JsonNode policy : policies) {
                    if (!"DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                        continue;
                    }
                    String scopeTemp = policy.path("scope").path("temperature").asText("ANY");
                    if (!temperature.equalsIgnoreCase(scopeTemp)) {
                        continue;
                    }
                    double rate = policy.path("params").path("rate").asDouble(fallbackRate);
                    return round2(rawAmount * rate);
                }
            }
        }
        return round2(rawAmount * fallbackRate);
    }

    private String temperaturePolicyRemark(JsonNode compiledRules, String temperature, String fallback) {
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return fallback;
        }
        for (JsonNode policy : policies) {
            if (!"DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            String scopeTemp = policy.path("scope").path("temperature").asText("ANY");
            if (!temperature.equalsIgnoreCase(scopeTemp)) {
                continue;
            }
            String name = policy.path("name").asText("");
            if (!name.isBlank()) {
                return name.contains("优惠") ? name : name + "优惠";
            }
            double rate = policy.path("params").path("rate").asDouble(1.0);
            if (rate > 0 && rate < 1.0) {
                return String.format("%.0f折优惠", rate * 10);
            }
        }
        return fallback;
    }

    private double sumRowsByTemperature(List<HospitalReconciliationRow> rows, String targetTemp) {
        double sum = 0;
        for (HospitalReconciliationRow row : rows) {
            String combined = str(row.getType()) + str(row.getPackName()) + str(row.getPackageMaterial());
            String rowTemp = BillingConditionEvaluator.resolveRowTemperature(combined);
            if (!targetTemp.equalsIgnoreCase(rowTemp)) {
                continue;
            }
            Double total = row.getCorrectedTotalPrice() != null
                    ? row.getCorrectedTotalPrice()
                    : row.getTotalPrice();
            if (total != null) {
                sum += total;
            }
        }
        return sum;
    }

    private void appendSpecialPackSettlementRows(
            List<SettlementFeeRow> rowsOut,
            List<HospitalReconciliationRow> rows,
            String hospitalName) {
        if (rows == null || rows.isEmpty() || !isHulanTcmHospital(hospitalName)) {
            return;
        }
        Map<String, PackSettlementAggregate> aggregates = new LinkedHashMap<>();
        for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
            aggregates.put(keyword, new PackSettlementAggregate());
        }
        for (HospitalReconciliationRow row : rows) {
            String packName = str(row.getPackName()).trim();
            for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
                if (!packName.equals(keyword)) {
                    continue;
                }
                PackSettlementAggregate agg = aggregates.get(keyword);
                int count = row.getPackCount() != null ? row.getPackCount() : 0;
                Double total = row.getCorrectedTotalPrice() != null
                        ? row.getCorrectedTotalPrice()
                        : row.getTotalPrice();
                agg.add(count, total != null ? total : 0, row.getUnitPrice());
            }
        }
        for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
            PackSettlementAggregate agg = aggregates.get(keyword);
            if (agg.packCount() <= 0 && Math.abs(agg.totalAmount()) < 0.01) {
                continue;
            }
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName(keyword)
                    .amount(round2(agg.totalAmount()))
                    .remark(formatPackRemark(agg))
                    .build());
        }
    }

    private String formatPackRemark(PackSettlementAggregate agg) {
        if (agg.packCount() <= 0) {
            double unit = agg.unitPrice() != null ? agg.unitPrice() : 0;
            return String.format("%.1f元*0个", unit);
        }
        double unit = agg.unitPrice() != null ? agg.unitPrice() : agg.totalAmount() / agg.packCount();
        return String.format("%.1f元*%d个", unit, agg.packCount());
    }

    private static String str(String value) {
        return value != null ? value : "";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class PackSettlementAggregate {
        private int packCount;
        private double totalAmount;
        private Double unitPrice;

        private void add(int count, double amount, Double unit) {
            packCount += count;
            totalAmount += amount;
            if (unit != null && unitPrice == null) {
                unitPrice = unit;
            }
        }

        private int packCount() {
            return packCount;
        }

        private double totalAmount() {
            return totalAmount;
        }

        private Double unitPrice() {
            return unitPrice;
        }
    }

    public double computeTotalAmount(List<SettlementFeeRow> feeRows) {
        java.util.Set<String> adjustedBaseNames = feeRows.stream()
                .map(SettlementFeeRow::getItemName)
                .filter(name -> name != null && name.endsWith("(减免后)"))
                .map(name -> name.substring(0, name.length() - "(减免后)".length()))
                .collect(java.util.stream.Collectors.toSet());
        return feeRows.stream()
                .filter(row -> {
                    String name = row.getItemName();
                    if (name == null) {
                        return true;
                    }
                    return !adjustedBaseNames.contains(name);
                })
                .mapToDouble(SettlementFeeRow::getAmount)
                .sum();
    }

    private void resequence(List<SettlementFeeRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setSequence(i + 1);
        }
    }

    private Optional<MonthlyBreakdown> parseMonthlyBreakdown(HospitalReconciliationJob job) {
        String json = job.getMonthlyBreakdown();
        if (json == null || json.isBlank()) {
            Double adjustment = job.getSettlementAdjustment();
            if (adjustment != null && Math.abs(adjustment) >= 0.01) {
                return Optional.of(new MonthlyBreakdown(null, adjustment));
            }
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Double adjustment = node.has("adjustment") ? node.get("adjustment").asDouble() : null;
            Double minCharge = node.has("minCharge") ? node.get("minCharge").asDouble() : null;
            if (adjustment == null) {
                adjustment = job.getSettlementAdjustment();
            }
            if (adjustment == null || Math.abs(adjustment) < 0.01) {
                return Optional.empty();
            }
            return Optional.of(new MonthlyBreakdown(minCharge, adjustment));
        } catch (Exception e) {
            log.warn("Failed to parse monthly_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private record MonthlyBreakdown(Double minCharge, Double adjustment) {}

    private Optional<UrgentBreakdown> parseUrgentBreakdown(HospitalReconciliationJob job) {
        String json = job.getUrgentBreakdown();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.has("urgentRowCount") || node.path("urgentRowCount").asInt(0) <= 0) {
                return Optional.empty();
            }
            return Optional.of(new UrgentBreakdown(
                    node.path("baseMultiplier").asDouble(1.25),
                    node.path("adjustedMultiplier").asDouble(1.025),
                    node.path("nominalSurcharge").asDouble(0),
                    node.path("adjustedSurcharge").asDouble(0),
                    node.path("urgentTripCount").asInt(0),
                    node.path("urgentLogisticsFeePerTrip").asDouble(150),
                    node.path("urgentLogisticsDiscountRate").asDouble(0.9),
                    node.path("nominalUrgentLogisticsTotal").asDouble(0),
                    node.path("adjustedUrgentLogisticsTotal").asDouble(0)
            ));
        } catch (Exception e) {
            log.warn("Failed to parse urgent_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DeductionBreakdown> parseDeductionBreakdown(HospitalReconciliationJob job) {
        String json = job.getDeductionBreakdown();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            double deductionAmount = node.has("deductionAmount")
                    ? node.path("deductionAmount").asDouble()
                    : -node.path("monthlyAmount").asDouble(0);
            if (Math.abs(deductionAmount) < 0.01) {
                return Optional.empty();
            }
            return Optional.of(new DeductionBreakdown(
                    deductionAmount,
                    node.path("policyName").asText("设备抵扣")
            ));
        } catch (Exception e) {
            log.warn("Failed to parse deduction_breakdown for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private record UrgentBreakdown(
            double baseMultiplier,
            double adjustedMultiplier,
            double nominalSurcharge,
            double adjustedSurcharge,
            int urgentTripCount,
            double urgentLogisticsFeePerTrip,
            double urgentLogisticsDiscountRate,
            double nominalUrgentLogisticsTotal,
            double adjustedUrgentLogisticsTotal
    ) {}

    private record DeductionBreakdown(double deductionAmount, String policyName) {}

    private Optional<Double> parseExternalInstrumentTotal(HospitalReconciliationJob job) {
        String json = job.getAllocationResult();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("externalInstrumentTotal")) {
                return Optional.of(node.path("externalInstrumentTotal").asDouble(0));
            }
        } catch (Exception e) {
            log.warn("Failed to parse external instrument total for job {}: {}", job.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    private static String formatAmount(double value) {
        return String.format("%.2f", value);
    }

    private String buildLogisticsRemark(
            HospitalReconciliationJob job,
            double cardDeducted,
            Map<String, Object> logisticsBreakdown) {
        StringBuilder remark = new StringBuilder();
        if (job.getLogisticsTripCount() != null && logisticsBreakdown != null) {
            Object feePerTrip = logisticsBreakdown.get("feePerTrip");
            if (feePerTrip instanceof Number fee) {
                remark.append(formatAmount(fee.doubleValue())).append("/次");
            }
            Object waivedTrips = logisticsBreakdown.get("waivedTrips");
            if (waivedTrips instanceof Number waived && waived.intValue() > 0) {
                if (!remark.isEmpty()) {
                    remark.append("（");
                } else {
                    remark.append("（");
                }
                remark.append("已免 ").append(waived.intValue()).append(" 次物流费）");
            } else if (job.getLogisticsTripCount() != null) {
                if (!remark.isEmpty()) {
                    remark.append(" · ");
                }
                remark.append(job.getLogisticsTripCount()).append(" 趟");
            }
        } else if (job.getLogisticsTripCount() != null) {
            remark.append(job.getLogisticsTripCount()).append(" 趟");
        }
        if (cardDeducted > 0) {
            if (!remark.isEmpty()) {
                remark.append("；");
            }
            remark.append("卡抵扣 ").append(cardDeducted);
        }
        return remark.toString();
    }

    private Map<String, Object> parseLogisticsBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.parseToMap(json);
    }

    @Getter
    @Builder
    public static class SettlementFeeRow {
        private int sequence;
        private String itemName;
        private double amount;
        private String remark;

        public void setSequence(int sequence) {
            this.sequence = sequence;
        }
    }
}
