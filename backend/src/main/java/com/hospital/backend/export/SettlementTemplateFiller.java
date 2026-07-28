package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.BillingMonthResolver;
import com.hospital.backend.service.BillingPolicyApplier;
import com.hospital.backend.service.BillingPolicyInspector;
import com.hospital.backend.service.UrgentFeeCalculator;
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

    private static final Map<String, Double> HULAN_TCM_SETTLEMENT_PACK_FIXED_PRICE = Map.of(
            "外科包", 249.5,
            "阑尾包", 288.0);

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();
    private final ReconciliationExportRowFilter exportRowFilter = new ReconciliationExportRowFilter();

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
        if (isXinfaHospital(job.getHospitalName())) {
            return buildXinfaFeeRows(job, compiledRules, rows);
        }
        List<SettlementFeeRow> rowsOut = new ArrayList<>();
        int seq = 1;
        String hospitalName = job.getHospitalName();
        String billingMonth = BillingMonthResolver.resolve(job);
        BillingPolicyInspector.SettlementOverride settlementOverride =
                BillingPolicyInspector.resolveSettlementOverride(compiledRules, billingMonth);
        double baseSterilize = settlementOverride != null && settlementOverride.sterilizeAmount() != null
                ? settlementOverride.sterilizeAmount()
                : resolveSettlementSterilizeBase(job, sterilizeTotal, compiledRules, rows, hospitalName);

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
            payableFee = resolvePayableLogisticsFee(job, logisticsBreakdown, payableFee);
            if (settlementOverride != null && settlementOverride.logisticsAmount() != null) {
                payableFee = settlementOverride.logisticsAmount();
            }
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(seq++)
                    .itemName(logisticsItemLabel(job.getHospitalName()))
                    .amount(payableFee)
                    .remark(buildLogisticsRemark(job, cardDeducted, logisticsBreakdown))
                    .build());
            if (cardDeducted > 0 && Math.abs(payableFee) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(seq++)
                        .itemName("物流卡抵扣")
                        .amount(-cardDeducted)
                        .remark("卡内扣减")
                        .build());
            }
        }

        appendSpecialPackSettlementRows(rowsOut, rows, job.getHospitalName());
        appendUrgentSettlementRows(rowsOut, job, compiledRules, hospitalName);
        appendSettlementExtraRows(rowsOut, job, compiledRules);

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

        resolveExternalInstrumentTotal(job, compiledRules, billingMonth).ifPresent(externalTotal -> {
            if (Math.abs(externalTotal) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName(externalInstrumentItemLabel(hospitalName))
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

    private static String externalInstrumentItemLabel(String hospitalName) {
        if (hospitalName != null && hospitalName.contains("第五")) {
            return "外来器械费用";
        }
        return "外来器械";
    }

    private double resolveSettlementSterilizeBase(
            HospitalReconciliationJob job,
            double sterilizeTotal,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows,
            String hospitalName) {
        if (rows != null && !rows.isEmpty()) {
            double excludedTotal = sumSettlementExcludedRowTotals(rows, hospitalName);
            if (excludedTotal > 0) {
                sterilizeTotal = round2(Math.max(0, sterilizeTotal - excludedTotal));
            }
        }
        return resolveBaseSterilizeTotal(sterilizeTotal, rows, hospitalName);
    }

    private double sumSettlementExcludedRowTotals(List<HospitalReconciliationRow> rows, String hospitalName) {
        boolean isHsz = hospitalName != null && hospitalName.contains("红十字妇产");
        boolean isShengYy = hospitalName != null && hospitalName.contains("黑龙江省医院");
        Map<String, List<HospitalReconciliationRow>> byOrder = isHsz
                ? rows.stream()
                        .filter(row -> row.getOrderNo() != null && !row.getOrderNo().isBlank())
                        .collect(java.util.stream.Collectors.groupingBy(row -> row.getOrderNo().trim()))
                : Map.of();
        double sum = 0;
        for (HospitalReconciliationRow row : rows) {
            if (shouldExcludeFromSettlementSterilizeBase(row, hospitalName, isHsz, isShengYy, byOrder)) {
                Double corrected = row.getCorrectedTotalPrice();
                if (corrected == null) {
                    corrected = row.getTotalPrice();
                }
                if (corrected != null) {
                    sum += corrected;
                }
            }
        }
        return sum;
    }

    private boolean shouldExcludeFromSettlementSterilizeBase(
            HospitalReconciliationRow row,
            String hospitalName,
            boolean isHsz,
            boolean isShengYy,
            Map<String, List<HospitalReconciliationRow>> byOrder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isUrgent", row.getIsUrgent());
        map.put("sheetName", row.getSheetName());
        map.put("billingNotes", row.getBillingNotes());
        if (UrgentFeeCalculator.isUrgentRow(map)) {
            return true;
        }
        if (isHsz && exportRowFilter.isHrbHszUrgentBillRow(row, byOrder)) {
            return true;
        }
        if (isShengYy && exportRowFilter.isShengYyRentalExportRow(row)) {
            return true;
        }
        String sheetName = row.getSheetName();
        if (sheetName != null && (sheetName.contains("加急") || sheetName.contains("结款"))) {
            return true;
        }
        return false;
    }

    private void appendUrgentSettlementRows(
            List<SettlementFeeRow> rowsOut,
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            String hospitalName) {
        String lineMode = BillingPolicyInspector.resolveUrgentLineMode(compiledRules);
        parseUrgentBreakdown(job).ifPresent(urgent -> {
            double sterilizeAmount = "total".equalsIgnoreCase(lineMode)
                    ? (urgent.nominalUrgentTotal() > 0
                            ? urgent.nominalUrgentTotal()
                            : urgent.nominalSurcharge())
                    : urgent.nominalSurcharge();
            if (Math.abs(sterilizeAmount) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急灭菌费")
                        .amount(sterilizeAmount)
                        .remark(String.format("%.0f%%", urgent.baseMultiplier() * 100))
                        .build());
            }
            double adjustedAmount = "total".equalsIgnoreCase(lineMode)
                    ? urgent.adjustedUrgentTotal()
                    : urgent.adjustedSurcharge();
            if (Math.abs(adjustedAmount) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName("加急灭菌费(减免后)")
                        .amount(adjustedAmount)
                        .remark(String.format("%.1f%%", urgent.adjustedMultiplier() * 100))
                        .build());
            }
            if (Math.abs(urgent.nominalUrgentLogisticsTotal()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName(urgentLogisticsNominalLabel(hospitalName))
                        .amount(urgent.nominalUrgentLogisticsTotal())
                        .remark(urgent.urgentTripCount() + " 趟 × "
                                + formatAmount(urgent.urgentLogisticsFeePerTrip()))
                        .build());
            }
            if (Math.abs(urgent.adjustedUrgentLogisticsTotal()) >= 0.01) {
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName(urgentLogisticsAdjustedLabel(hospitalName))
                        .amount(urgent.adjustedUrgentLogisticsTotal())
                        .remark(String.format("%.0f%%", urgent.urgentLogisticsDiscountRate() * 100))
                        .build());
            }
        });
    }

    private void appendSettlementExtraRows(
            List<SettlementFeeRow> rowsOut,
            HospitalReconciliationJob job,
            JsonNode compiledRules) {
        if (compiledRules == null) {
            return;
        }
        BillingPolicyInspector.OptionalSettlementExtra extra =
                BillingPolicyInspector.resolveSettlementExtra(compiledRules, BillingMonthResolver.resolve(job));
        if (extra == null || Math.abs(extra.amount()) < 0.01) {
            return;
        }
        rowsOut.add(SettlementFeeRow.builder()
                .sequence(rowsOut.size() + 1)
                .itemName(extra.itemName())
                .amount(extra.amount())
                .remark("")
                .build());
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

    private static boolean isXinfaHospital(String hospitalName) {
        return hospitalName != null && hospitalName.contains("新发");
    }

    private static String urgentLogisticsNominalLabel(String hospitalName) {
        return "加急物流费";
    }

    private static String urgentLogisticsAdjustedLabel(String hospitalName) {
        if (isXinfaHospital(hospitalName)) {
            return "减免后加急物流费";
        }
        return "加急物流费(减免后)";
    }

    private List<SettlementFeeRow> buildXinfaFeeRows(
            HospitalReconciliationJob job,
            JsonNode compiledRules,
            List<HospitalReconciliationRow> rows) {
        List<SettlementFeeRow> rowsOut = new ArrayList<>();
        String billingMonth = BillingMonthResolver.resolve(job);
        BillingPolicyInspector.SettlementOverride settlementOverride =
                BillingPolicyInspector.resolveSettlementOverride(compiledRules, billingMonth);
        double systemSterilize = round2(sumXinfaSystemSterilize(rows));
        double dressingTotal = round2(sumXinfaDressing(rows));
        double htDiscountRate = resolveXinfaSettlementDiscountRate(compiledRules);
        double discountedHt = round2(systemSterilize * htDiscountRate);
        if (settlementOverride != null && settlementOverride.xinfaSystemSterilize() != null) {
            systemSterilize = settlementOverride.xinfaSystemSterilize();
        }
        if (settlementOverride != null && settlementOverride.xinfaHtDiscounted() != null) {
            discountedHt = settlementOverride.xinfaHtDiscounted();
        }
        if (settlementOverride != null && settlementOverride.xinfaDressing() != null) {
            dressingTotal = settlementOverride.xinfaDressing();
        }

        rowsOut.add(SettlementFeeRow.builder()
                .sequence(1)
                .itemName("系统灭菌费用")
                .amount(systemSterilize)
                .remark("")
                .build());
        rowsOut.add(SettlementFeeRow.builder()
                .sequence(2)
                .itemName("高温75折后费用（实收）")
                .amount(discountedHt)
                .remark("高温75折优惠")
                .build());
        if (Math.abs(dressingTotal) >= 0.01) {
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName("敷料")
                    .amount(dressingTotal)
                    .remark("")
                    .build());
        }

        if (shouldShowLogisticsRow(job, compiledRules)) {
            Map<String, Object> logisticsBreakdown = parseLogisticsBreakdown(job.getLogisticsBreakdown());
            double payableFee = logisticsBreakdown != null && logisticsBreakdown.get("payableFee") instanceof Number p
                    ? p.doubleValue() : (job.getLogisticsFee() != null ? job.getLogisticsFee() : 0);
            payableFee = resolvePayableLogisticsFee(job, logisticsBreakdown, payableFee);
            if (settlementOverride != null && settlementOverride.logisticsAmount() != null) {
                payableFee = settlementOverride.logisticsAmount();
            }
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName("物流费用")
                    .amount(payableFee)
                    .remark(buildLogisticsRemark(job, 0, logisticsBreakdown))
                    .build());
        }

        appendUrgentSettlementRows(rowsOut, job, compiledRules, job.getHospitalName());

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

        if (settlementOverride != null && settlementOverride.minChargeAdjustment() != null) {
            double adjustment = settlementOverride.minChargeAdjustment();
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName(Math.abs(adjustment) < 0.01 ? "低消补差" : (adjustment > 0 ? "低消补差" : "封顶调减"))
                    .amount(adjustment)
                    .remark("")
                    .build());
        } else if (!BillingPolicyInspector.settlementOmitMinChargeRow(compiledRules)) {
            parseMonthlyBreakdown(job).ifPresent(breakdown -> {
                double adjustment = breakdown.adjustment() != null ? breakdown.adjustment() : 0;
                rowsOut.add(SettlementFeeRow.builder()
                        .sequence(rowsOut.size() + 1)
                        .itemName(Math.abs(adjustment) < 0.01 ? "低消补差" : (adjustment > 0 ? "低消补差" : "封顶调减"))
                        .amount(adjustment)
                        .remark(Optional.ofNullable(breakdown.minCharge())
                                .map(m -> "低消 " + m).orElse(""))
                        .build());
            });
        } else if (job.getSettlementAdjustment() != null) {
            double adjustment = job.getSettlementAdjustment();
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName(adjustment > 0 ? "低消补差" : "封顶调减")
                    .amount(adjustment)
                    .remark("")
                    .build());
        }

        resequence(rowsOut);
        return rowsOut;
    }

    private double resolveXinfaSettlementDiscountRate(JsonNode compiledRules) {
        if (compiledRules == null) {
            return 0.75;
        }
        JsonNode policies = compiledRules.path("billingPolicies");
        if (!policies.isArray()) {
            return 0.75;
        }
        for (JsonNode policy : policies) {
            if (!"DISCOUNT".equalsIgnoreCase(policy.path("policyType").asText())) {
                continue;
            }
            String stage = policy.path("params").path("applyStage").asText("");
            if (!"settlement_only".equalsIgnoreCase(stage)) {
                continue;
            }
            double rate = policy.path("params").path("rate").asDouble(0.75);
            if (rate > 0 && rate <= 1.0) {
                return rate;
            }
        }
        return 0.75;
    }

    private double sumXinfaSystemSterilize(List<HospitalReconciliationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (HospitalReconciliationRow row : rows) {
            if (!isXinfaSystemSterilizeRow(row)) {
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

    private double sumXinfaDressing(List<HospitalReconciliationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (HospitalReconciliationRow row : rows) {
            if (!isXinfaOperatingRoomLowTempDressingRow(row)) {
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

    private static boolean isXinfaOperatingRoomLowTempDressingRow(HospitalReconciliationRow row) {
        String sheet = str(row.getSheetName());
        if (!sheet.contains("手术室")) {
            return false;
        }
        String type = str(row.getType());
        String material = str(row.getPackageMaterial());
        String pack = str(row.getPackName());
        String combined = (type + material + pack).toLowerCase();
        if (!combined.contains("低温") && !combined.contains("等离子") && !combined.contains("eto")) {
            return false;
        }
        return type.contains("敷料") || type.contains("辅料") || material.contains("敷料") || pack.contains("敷料");
    }

    private static boolean isDressingRow(HospitalReconciliationRow row) {
        String type = str(row.getType());
        String material = str(row.getPackageMaterial());
        String pack = str(row.getPackName());
        return type.contains("敷料") || type.contains("辅料") || material.contains("敷料") || pack.contains("敷料");
    }

    private static boolean isXinfaPlasmaDressingRow(HospitalReconciliationRow row) {
        String type = str(row.getType());
        return type.contains("低温等离子");
    }

    private static boolean isXinfaSystemSterilizeRow(HospitalReconciliationRow row) {
        String type = str(row.getType());
        if (type.contains("低温等离子")) {
            return false;
        }
        if (isDressingRow(row)) {
            return false;
        }
        return type.startsWith("额外包") || type.startsWith("器械包") || type.startsWith("单包装");
    }

    private double sumSpecialPackTotals(List<HospitalReconciliationRow> rows) {
        Map<String, PackSettlementAggregate> aggregates = new LinkedHashMap<>();
        for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
            aggregates.put(keyword, new PackSettlementAggregate());
        }
        for (HospitalReconciliationRow row : rows) {
            String packName = str(row.getPackName()).trim();
            for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
                if (!matchesSettlementPackKeyword(packName, keyword)) {
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
        double sum = 0;
        for (String keyword : HULAN_TCM_SETTLEMENT_PACK_KEYWORDS) {
            PackSettlementAggregate agg = aggregates.get(keyword);
            if (agg.packCount() <= 0 && Math.abs(agg.totalAmount()) < 0.01) {
                continue;
            }
            Double fixedPrice = HULAN_TCM_SETTLEMENT_PACK_FIXED_PRICE.get(keyword);
            if (fixedPrice != null) {
                if ("外科包".equals(keyword)) {
                    sum += fixedPrice;
                } else {
                    sum += fixedPrice * Math.max(1, agg.packCount());
                }
            } else {
                sum += agg.totalAmount();
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
        if (BillingPolicyInspector.settlementOmitLogisticsRow(compiledRules)) {
            return false;
        }
        Map<String, Object> logisticsBreakdown = parseLogisticsBreakdown(job.getLogisticsBreakdown());
        double payableFee = logisticsBreakdown != null && logisticsBreakdown.get("payableFee") instanceof Number p
                ? p.doubleValue()
                : (job.getLogisticsFee() != null ? job.getLogisticsFee() : 0);
        payableFee = resolvePayableLogisticsFee(job, logisticsBreakdown, payableFee);

        if (BillingPolicyInspector.settlementOmitZeroRows(compiledRules) && Math.abs(payableFee) < 0.01) {
            return false;
        }

        if (BillingPolicyInspector.hasLogisticsPolicy(compiledRules)) {
            double feePerTrip = BillingPolicyInspector.resolveLogisticsFeePerTrip(compiledRules);
            if (feePerTrip <= 0) {
                return false;
            }
            return job.getLogisticsFee() != null || Math.abs(payableFee) >= 0.01 || logisticsBreakdown != null;
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
                if (!matchesSettlementPackKeyword(packName, keyword)) {
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
            Double fixedPrice = HULAN_TCM_SETTLEMENT_PACK_FIXED_PRICE.get(keyword);
            double amount = fixedPrice != null
                    ? round2(fixedPrice * Math.max(1, agg.packCount()))
                    : round2(agg.totalAmount());
            if ("外科包".equals(keyword) && fixedPrice != null) {
                amount = fixedPrice;
            }
            rowsOut.add(SettlementFeeRow.builder()
                    .sequence(rowsOut.size() + 1)
                    .itemName(keyword)
                    .amount(amount)
                    .remark(formatPackRemark(agg, fixedPrice))
                    .build());
        }
    }

    private static boolean matchesSettlementPackKeyword(String packName, String keyword) {
        if (packName.equals(keyword)) {
            return true;
        }
        return packName.endsWith("-" + keyword) || packName.contains(keyword);
    }

    private String formatPackRemark(PackSettlementAggregate agg, Double fixedUnitPrice) {
        if (agg.packCount() <= 0) {
            double unit = fixedUnitPrice != null ? fixedUnitPrice
                    : (agg.unitPrice() != null ? agg.unitPrice() : 0);
            return String.format("%.1f元*0个", unit);
        }
        double unit = fixedUnitPrice != null ? fixedUnitPrice
                : (agg.unitPrice() != null ? agg.unitPrice() : agg.totalAmount() / agg.packCount());
        return String.format("%.1f元*%d个", unit, agg.packCount());
    }

    private String formatPackRemark(PackSettlementAggregate agg) {
        return formatPackRemark(agg, null);
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
                if (node.path("nominalUrgentTotal").asDouble(0) <= 0
                        && node.path("nominalSurcharge").asDouble(0) <= 0) {
                    return Optional.empty();
                }
            }
            return Optional.of(new UrgentBreakdown(
                    node.path("baseMultiplier").asDouble(1.25),
                    node.path("adjustedMultiplier").asDouble(1.025),
                    node.path("nominalUrgentTotal").asDouble(0),
                    node.path("adjustedUrgentTotal").asDouble(0),
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
            double nominalUrgentTotal,
            double adjustedUrgentTotal,
            double nominalSurcharge,
            double adjustedSurcharge,
            int urgentTripCount,
            double urgentLogisticsFeePerTrip,
            double urgentLogisticsDiscountRate,
            double nominalUrgentLogisticsTotal,
            double adjustedUrgentLogisticsTotal
    ) {}

    private record DeductionBreakdown(double deductionAmount, String policyName) {}

    private Optional<Double> resolveExternalInstrumentTotal(
            HospitalReconciliationJob job, JsonNode compiledRules, String billingMonth) {
        BillingPolicyInspector.SettlementOverride override =
                BillingPolicyInspector.resolveSettlementOverride(compiledRules, billingMonth);
        if (override != null && override.externalInstrumentAmount() != null) {
            return Optional.of(override.externalInstrumentAmount());
        }
        return parseExternalInstrumentTotal(job);
    }

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

    private double resolvePayableLogisticsFee(
            HospitalReconciliationJob job,
            Map<String, Object> logisticsBreakdown,
            double payableFee) {
        if (logisticsBreakdown == null) {
            return payableFee;
        }
        Object waivedObj = logisticsBreakdown.get("waivedTrips");
        Object tripObj = logisticsBreakdown.get("tripCount");
        int trips = tripObj instanceof Number tripNumber
                ? tripNumber.intValue()
                : (job.getLogisticsTripCount() != null ? job.getLogisticsTripCount() : 0);
        if (waivedObj instanceof Number waived && trips > 0 && waived.intValue() >= trips) {
            return 0;
        }
        if (payableFee <= 0.01 && trips > 0 && waivedObj instanceof Number waived && waived.intValue() > 0) {
            Object feePerTrip = logisticsBreakdown.get("feePerTrip");
            if (feePerTrip instanceof Number fee) {
                int billable = Math.max(0, trips - waived.intValue());
                return round2(billable * fee.doubleValue());
            }
        }
        return payableFee;
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
