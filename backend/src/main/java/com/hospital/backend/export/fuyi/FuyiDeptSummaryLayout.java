package com.hospital.backend.export.fuyi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附一分科室汇总双栏布局：对齐客户处理后表格（6月各科室费用汇总.xlsx）。
 */
public final class FuyiDeptSummaryLayout {

    public static final String SHEET_NAME = "汇总";
    public static final String WASH_FEE_LABEL = "手术室（一区）洗涤费";
    public static final String LOGISTICS_LABEL = "物流费";

    /** 有洗涤费时左栏 28 行（含洗涤费，末行消化二科，不含心内五）。 */
    private static final List<String> LEFT_WITH_WASH_FEE = List.of(
            "手术室(一区)",
            WASH_FEE_LABEL,
            "手术室(二区)",
            "妇一",
            "妇二",
            "妇三",
            "骨一",
            "骨二",
            "骨三",
            "骨四",
            "外一",
            "外二",
            "外三",
            "外四",
            "肛肠门诊",
            "宫腔镜",
            "超声",
            "眼科病房",
            "眼科手术室（一）",
            "眼科手术室（二）",
            "眼科门诊322",
            "眼科门诊357",
            "介入科",
            "周围血管一",
            "周围血管二",
            "静配中心",
            "消化一",
            "消化二科");

    /** 无洗涤费时左栏 28 行（末行心内五）。 */
    private static final List<String> LEFT_WITHOUT_WASH_FEE = List.of(
            "手术室(一区)",
            "手术室(二区)",
            "妇一",
            "妇二",
            "妇三",
            "骨一",
            "骨二",
            "骨三",
            "骨四",
            "外一",
            "外二",
            "外三",
            "外四",
            "肛肠门诊",
            "宫腔镜",
            "超声",
            "眼科病房",
            "眼科手术室（一）",
            "眼科手术室（二）",
            "眼科门诊322",
            "眼科门诊357",
            "介入科",
            "周围血管一",
            "周围血管二",
            "静配中心",
            "消化一",
            "消化二科",
            "心内五");

    /** 右栏 27 科室 + 末行物流费。 */
    private static final List<String> BASE_RIGHT = List.of(
            "产科",
            "计划生育手术室",
            "耳鼻喉病房",
            "耳鼻喉门诊",
            "妇科门诊",
            "妇二门诊561",
            "产科门诊236",
            "内镜诊疗室",
            "肛肠科",
            "透析",
            "急诊",
            "口腔科",
            "门诊手术室",
            "中医经典病房",
            "重症医学科",
            "针灸一",
            "针灸二",
            "针灸三",
            "针灸五",
            "肾病一科",
            "肾病二科",
            "清洁区（手术室）",
            "血液",
            "心血管二",
            "消化微创病房",
            "内分泌（二）",
            "康复二");

    private static final Map<String, String> SHEET_ALIASES = Map.ofEntries(
            Map.entry("眼科门诊手术室（一）", "眼科手术室（一）"),
            Map.entry("眼科门诊手术室（二）", "眼科手术室（二）"),
            Map.entry("康复二科", "康复二"),
            Map.entry("血液科", "血液"));

    private FuyiDeptSummaryLayout() {
    }

    public record DeptEntry(String department, Double amount) {
    }

    public record Layout(List<DeptEntry> left, List<DeptEntry> right, double grandTotal) {
    }

    public static Map<String, Double> normalizeDeptTotals(Map<String, Double> rawTotals) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (rawTotals == null) {
            return normalized;
        }
        for (Map.Entry<String, Double> entry : rawTotals.entrySet()) {
            String key = canonicalDeptName(entry.getKey());
            if (key.isBlank() || LOGISTICS_LABEL.equals(key) || WASH_FEE_LABEL.equals(key)) {
                continue;
            }
            double value = entry.getValue() != null ? entry.getValue() : 0.0;
            normalized.merge(key, value, Double::sum);
        }
        return normalized;
    }

    public static Layout build(Map<String, Double> rawTotals, Double washFee, Double logisticsFee) {
        Map<String, Double> totals = normalizeDeptTotals(rawTotals);
        boolean hasWashFee = washFee != null && washFee > 0.005;
        List<DeptEntry> left = buildLeftColumn(totals, washFee, hasWashFee);
        List<DeptEntry> right = buildRightColumn(totals, logisticsFee);

        double grandTotal = 0.0;
        for (DeptEntry entry : left) {
            grandTotal += amountOrZero(entry.amount());
        }
        for (DeptEntry entry : right) {
            grandTotal += amountOrZero(entry.amount());
        }
        return new Layout(left, right, roundCurrency(grandTotal));
    }

    private static List<DeptEntry> buildLeftColumn(
            Map<String, Double> totals,
            Double washFee,
            boolean hasWashFee) {
        List<String> names = hasWashFee ? LEFT_WITH_WASH_FEE : LEFT_WITHOUT_WASH_FEE;
        List<DeptEntry> entries = new ArrayList<>();
        for (String name : names) {
            if (WASH_FEE_LABEL.equals(name)) {
                entries.add(new DeptEntry(name, roundCurrency(washFee)));
            } else {
                entries.add(new DeptEntry(name, lookupAmount(totals, name)));
            }
        }
        return entries;
    }

    private static List<DeptEntry> buildRightColumn(Map<String, Double> totals, Double logisticsFee) {
        List<DeptEntry> entries = new ArrayList<>();
        for (String name : BASE_RIGHT) {
            entries.add(new DeptEntry(name, lookupAmount(totals, name)));
        }
        double logistics = logisticsFee != null ? logisticsFee : 0.0;
        entries.add(new DeptEntry(
                LOGISTICS_LABEL,
                logistics > 0.005 ? roundCurrency(logistics) : null));
        return entries;
    }

    private static Double lookupAmount(Map<String, Double> totals, String department) {
        String canonical = canonicalDeptName(department);
        if (!totals.containsKey(canonical)) {
            return null;
        }
        double value = totals.get(canonical);
        if (Math.abs(value) < 0.005) {
            return null;
        }
        return roundCurrency(value);
    }

    static String canonicalDeptName(String sheetName) {
        if (sheetName == null) {
            return "";
        }
        String trimmed = sheetName.trim();
        return SHEET_ALIASES.getOrDefault(trimmed, trimmed);
    }

    private static double amountOrZero(Double amount) {
        return amount != null ? amount : 0.0;
    }

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
