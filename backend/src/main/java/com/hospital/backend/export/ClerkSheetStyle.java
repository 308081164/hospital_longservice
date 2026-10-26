package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.BillingMonthResolver;
import com.hospital.backend.service.BillingPolicyApplier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 10.20 内勤样式表的版式。金额来自账单和已有规则参数，不写入样表里的示例数字。
 */
public final class ClerkSheetStyle {

    static final String MERGED_TITLE_HOSPITALS = "三辅社区服务中心、哈尔滨市香坊区中医院";
    static final String SANFU_REMARK = "三辅社区";
    static final String XIANGFANG_REMARK = "香坊中医院";

    private ClerkSheetStyle() {
    }

    public static boolean applies(JsonNode compiled) {
        return isMerged(compiled) || isGuoyao(compiled) || discountColumnRate(compiled) != null;
    }

    public static boolean isMerged(JsonNode compiled) {
        return compiled != null && compiled.hasNonNull("mergedSettlement");
    }

    public static boolean isGuoyao(JsonNode compiled) {
        return compiled != null && compiled.hasNonNull("guoyaoSettlement");
    }

    /** 附三结款函「原价 / N折后」并列。没有这项参数时返回 null，不另造折扣。 */
    public static Double discountColumnRate(JsonNode compiled) {
        if (compiled == null) {
            return null;
        }
        for (JsonNode layout : compiled.path("exportLayouts")) {
            JsonNode params = layout.path("params");
            if (!params.path("showOriginalAndDiscount").asBoolean(false)) {
                continue;
            }
            if (!params.has("discountRate") || !params.path("discountRate").isNumber()) {
                return null;
            }
            return params.path("discountRate").asDouble();
        }
        return null;
    }

    public static String mergedTitle(HospitalReconciliationJob job) {
        Integer month = monthOf(job);
        if (month == null) {
            return MERGED_TITLE_HOSPITALS + "结款通知函";
        }
        return MERGED_TITLE_HOSPITALS + month + "月份结款通知函";
    }

    public static String mergedLogisticsRemark(JsonNode compiled) {
        Double fee = logisticsFeePerTrip(compiled);
        if (fee == null) {
            JsonNode shared = compiled.path("mergedSettlement").path("sharedLogisticsFee");
            if (shared.isNumber()) {
                fee = shared.asDouble();
            }
        }
        if (fee == null) {
            return "（当日合并计费）";
        }
        return formatUnit(fee) + "/次（当日合并计费）";
    }

    public static List<LetterLine> mergedLines(
            JsonNode compiled,
            double sanfuSterilize,
            double xiangfangSterilize,
            double logisticsAmount) {
        List<LetterLine> lines = new ArrayList<>();
        lines.add(new LetterLine("1", "灭菌费用", round2(sanfuSterilize), null, SANFU_REMARK));
        lines.add(new LetterLine("2", "灭菌费用", round2(xiangfangSterilize), null, XIANGFANG_REMARK));
        lines.add(new LetterLine("3", "物流费用", round2(logisticsAmount), null, mergedLogisticsRemark(compiled)));
        return lines;
    }

    public static double letterTotal(List<LetterLine> lines) {
        double total = 0;
        for (LetterLine line : lines) {
            if (line.amount() != null) {
                total += line.amount();
            }
        }
        return round2(total);
    }

    public static boolean campus3(JsonNode compiled) {
        return "campus3".equals(compiled.path("guoyaoSettlement").path("campus").asText(""));
    }

    public static List<GuoyaoLine> guoyaoLines(JsonNode compiled, List<HospitalReconciliationRow> rows) {
        double divisor = compiled.path("guoyaoSettlement").path("instrumentPackDivisor").asDouble(5.5);
        int block = compiled.path("guoyaoSettlement").path("allocationDivisor").asInt(15);
        if (divisor <= 0) {
            divisor = 5.5;
        }
        if (block <= 0) {
            block = 15;
        }
        boolean thirdCampus = campus3(compiled);
        Map<String, GuoyaoBucket> buckets = new LinkedHashMap<>();
        for (String label : guoyaoLabels(thirdCampus)) {
            buckets.put(label, new GuoyaoBucket(instrumentFormula(label)));
        }
        if (rows != null) {
            for (HospitalReconciliationRow row : rows) {
                if (row == null || "skipped".equalsIgnoreCase(row.getStatus())) {
                    continue;
                }
                String label = guoyaoLabel(row, thirdCampus);
                GuoyaoBucket bucket = buckets.computeIfAbsent(label, key -> new GuoyaoBucket(instrumentFormula(key)));
                bucket.add(rowAmount(row), rowCount(row));
            }
        }
        List<GuoyaoLine> lines = new ArrayList<>();
        for (Map.Entry<String, GuoyaoBucket> entry : buckets.entrySet()) {
            lines.add(entry.getValue().toLine(entry.getKey(), divisor, block));
        }
        return lines;
    }

    public static double guoyaoSterilizeTotal(List<GuoyaoLine> lines) {
        double total = 0;
        for (GuoyaoLine line : lines) {
            if (line.billedAmount() != null) {
                total += line.billedAmount();
            }
        }
        return round2(total);
    }

    static List<String> guoyaoLabels(boolean thirdCampus) {
        if (thirdCampus) {
            return List.of("其余科室器械", "其余科室额外包", "敷料包");
        }
        return List.of(
                "手术室器械包",
                "其余科室器械",
                "手术室额外包",
                "其余科室额外包",
                "手术室敷料包",
                "其他科室敷料包",
                "手术室低温器械",
                "其余科室低温器械",
                "手术室单包装",
                "其余科室单包装");
    }

    static String guoyaoLabel(HospitalReconciliationRow row, boolean thirdCampus) {
        String blob = ((row.getType() == null ? "" : row.getType())
                + (row.getPackName() == null ? "" : row.getPackName())
                + (row.getCategoryNo() == null ? "" : row.getCategoryNo()))
                .replaceAll("\\s+", "");
        boolean operatingRoom = !thirdCampus && isOperatingRoom(row.getSheetName());
        String kind;
        if (blob.contains("额外")) {
            kind = "额外包";
        } else if (blob.contains("敷料")) {
            kind = thirdCampus ? "敷料包" : "敷料包";
        } else if (blob.contains("单包")) {
            kind = "单包装";
        } else if (blob.contains("低温")) {
            kind = "低温器械";
        } else {
            kind = "器械";
        }
        if (thirdCampus) {
            return switch (kind) {
                case "额外包" -> "其余科室额外包";
                case "敷料包" -> "敷料包";
                case "单包装" -> "其余科室单包装";
                case "低温器械" -> "其余科室低温器械";
                default -> "其余科室器械";
            };
        }
        String side = operatingRoom ? "手术室" : "其余科室";
        if ("敷料包".equals(kind) && !operatingRoom) {
            return "其他科室敷料包";
        }
        if ("器械".equals(kind)) {
            return operatingRoom ? "手术室器械包" : "其余科室器械";
        }
        return side + kind;
    }

    public static List<LetterLine> discountColumnLines(
            JsonNode compiled,
            double sterilizeOriginal,
            double externalOriginal,
            double logisticsAmount) {
        Double rate = discountColumnRate(compiled);
        if (rate == null) {
            return List.of();
        }
        int fold = (int) Math.round(rate * 10);
        String discountedHeader = fold + "折后（元）";
        List<LetterLine> lines = new ArrayList<>();
        lines.add(new LetterLine(
                "1", "灭菌费用", round2(sterilizeOriginal), round2(sterilizeOriginal * rate), ""));
        lines.add(new LetterLine(
                "2", "外来器械", round2(externalOriginal), round2(externalOriginal * rate), ""));
        lines.add(new LetterLine("4", "物流费用", round2(logisticsAmount), null, ""));
        lines.add(new LetterLine("header", discountedHeader, null, null, ""));
        return lines;
    }

    public static double discountColumnTotal(List<LetterLine> lines) {
        double total = 0;
        for (LetterLine line : lines) {
            if ("header".equals(line.index())) {
                continue;
            }
            if ("物流费用".equals(line.item())) {
                total += line.amount() == null ? 0 : line.amount();
            } else if (line.discountedAmount() != null) {
                total += line.discountedAmount();
            }
        }
        return round2(total);
    }

    public static double externalOriginal(HospitalReconciliationJob job, List<HospitalReconciliationRow> rows) {
        double fromRows = 0;
        if (rows != null) {
            for (HospitalReconciliationRow row : rows) {
                if (isExternal(row)) {
                    fromRows += rowAmount(row);
                }
            }
        }
        if (fromRows > 0) {
            return round2(fromRows);
        }
        if (job != null && job.getAllocationResult() != null && !job.getAllocationResult().isBlank()) {
            try {
                JsonNode node = com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .readTree(job.getAllocationResult());
                if (node.has("externalInstrumentTotal")) {
                    return round2(node.path("externalInstrumentTotal").asDouble(0));
                }
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static boolean isExternal(HospitalReconciliationRow row) {
        if (row == null) {
            return false;
        }
        String blob = (safe(row.getType()) + safe(row.getPackName()) + safe(row.getSheetName())
                + safe(row.getCategoryNo())).replaceAll("\\s+", "");
        return blob.contains("外来");
    }

    public static List<DeptPriceLine> zuyanDeptLines(
            List<HospitalReconciliationRow> rows,
            double logisticsFee) {
        Map<String, Double> deptSums = new LinkedHashMap<>();
        if (rows != null) {
            for (HospitalReconciliationRow row : rows) {
                if (row == null || "skipped".equalsIgnoreCase(row.getStatus())) {
                    continue;
                }
                String dept = row.getSheetName() == null || row.getSheetName().isBlank()
                        ? "(默认)"
                        : row.getSheetName().trim();
                deptSums.merge(dept, rowAmount(row), Double::sum);
            }
        }
        double sterilize = 0;
        for (double value : deptSums.values()) {
            sterilize += value;
        }
        sterilize = round2(sterilize);
        List<DeptPriceLine> lines = new ArrayList<>();
        double allocated = 0;
        int index = 0;
        int size = deptSums.size();
        for (Map.Entry<String, Double> entry : deptSums.entrySet()) {
            index++;
            double base = round2(entry.getValue());
            double share;
            if (index == size) {
                share = round2(logisticsFee - allocated);
            } else if (sterilize <= 0) {
                share = 0;
            } else {
                share = round2(logisticsFee * base / sterilize);
                allocated = round2(allocated + share);
            }
            lines.add(new DeptPriceLine(entry.getKey(), base, share, round2(base + share)));
        }
        return lines;
    }

    public static String zuyanTitle(HospitalReconciliationJob job) {
        String month = BillingMonthResolver.resolve(job);
        if (month != null && month.length() >= 7) {
            int year = Integer.parseInt(month.substring(0, 4));
            int mon = Integer.parseInt(month.substring(5, 7));
            return "祖研-黑龙江省中医医院（南岗院区）" + year + "年" + mon + "月科室灭菌价格汇总";
        }
        return "祖研-黑龙江省中医医院（南岗院区）科室灭菌价格汇总";
    }

    public static boolean isZuyanNangang(String hospitalName) {
        if (hospitalName == null) {
            return false;
        }
        return hospitalName.contains("祖研") && hospitalName.contains("南岗");
    }

    public static boolean isZy3(String hospitalName) {
        return hospitalName != null && hospitalName.contains("附属第三医院");
    }

    static boolean instrumentFormula(String label) {
        return "手术室器械包".equals(label) || "其余科室器械".equals(label);
    }

    static boolean isOperatingRoom(String sheetName) {
        if (sheetName == null) {
            return false;
        }
        String name = sheetName.replaceAll("\\s+", "");
        return name.contains("手术室") || name.contains("妇科手术室");
    }

    static Double logisticsFeePerTrip(JsonNode compiled) {
        if (compiled == null) {
            return null;
        }
        for (JsonNode policy : compiled.path("billingPolicies")) {
            if (!"LOGISTICS".equals(policy.path("policyType").asText())) {
                continue;
            }
            JsonNode fee = policy.path("params").path("feePerTrip");
            if (fee.isNumber()) {
                return fee.asDouble();
            }
        }
        return null;
    }

    static Integer monthOf(HospitalReconciliationJob job) {
        var period = SettlementPeriodResolver.resolve(job);
        if (period.isPresent()) {
            return period.get().end().getMonthValue();
        }
        String month = BillingMonthResolver.resolve(job);
        if (month != null && month.length() >= 7) {
            return Integer.parseInt(month.substring(5, 7));
        }
        return null;
    }

    public static String formatUnit(double fee) {
        if (Math.abs(fee - Math.rint(fee)) < 0.001) {
            return String.valueOf((long) Math.rint(fee));
        }
        return String.valueOf(round2(fee));
    }

    public static double round2(double value) {
        return BillingPolicyApplier.round(value);
    }

    private static double rowAmount(HospitalReconciliationRow row) {
        Double total = BillExportPriceResolver.resolveTotalPrice(row);
        if (total != null) {
            return total;
        }
        if (row.getCorrectedTotalPrice() != null) {
            return row.getCorrectedTotalPrice();
        }
        return row.getTotalPrice() != null ? row.getTotalPrice() : 0;
    }

    private static int rowCount(HospitalReconciliationRow row) {
        if (row.getInstrumentCount() != null && row.getInstrumentCount() > 0) {
            return row.getInstrumentCount();
        }
        if (row.getPackCount() != null && row.getPackCount() > 0) {
            return row.getPackCount();
        }
        return 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record LetterLine(String index, String item, Double amount, Double discountedAmount, String remark) {
    }

    public record GuoyaoLine(String category, Integer handles, Integer accounted, Double billedAmount) {
    }

    public record DeptPriceLine(String department, double baseAmount, double logisticsAmount, double totalAmount) {
    }

    private static final class GuoyaoBucket {
        private final boolean instrumentFormula;
        private double amount;
        private int count;

        private GuoyaoBucket(boolean instrumentFormula) {
            this.instrumentFormula = instrumentFormula;
        }

        private void add(double rowAmount, int rowCount) {
            this.amount += rowAmount;
            this.count += rowCount;
        }

        private GuoyaoLine toLine(String label, double divisor, int block) {
            if (!instrumentFormula) {
                return new GuoyaoLine(label, null, count, round2(amount));
            }
            int handles = (int) Math.floor(amount / divisor);
            int accounted = handles <= 0 ? 0 : (handles / block) * 10 + (handles % block);
            return new GuoyaoLine(label, handles, accounted, round2(accounted * divisor));
        }
    }
}
