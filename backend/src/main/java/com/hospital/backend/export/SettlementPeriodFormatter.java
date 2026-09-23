package com.hospital.backend.export;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结款函账期解析与中文格式化（标题、结算周期行、落款日期）。
 */
public final class SettlementPeriodFormatter {

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");
    private static final Pattern CN_DATE = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern RANGE_SEPARATOR = Pattern.compile("(?:至|到|—|–|-|\\bto\\b)", Pattern.CASE_INSENSITIVE);

    private SettlementPeriodFormatter() {
    }

    public record BillingPeriod(LocalDate start, LocalDate end) {
    }

    public static Optional<BillingPeriod> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        List<LocalDate> dates = extractDates(trimmed);
        if (dates.isEmpty()) {
            return Optional.empty();
        }
        if (dates.size() == 1) {
            LocalDate only = dates.get(0);
            return Optional.of(new BillingPeriod(only, only));
        }
        if (RANGE_SEPARATOR.matcher(trimmed).find()) {
            return Optional.of(new BillingPeriod(dates.get(0), dates.get(dates.size() - 1)));
        }
        return Optional.of(new BillingPeriod(dates.get(0), dates.get(dates.size() - 1)));
    }

    /** 结款函 D9：从:… 至: … 灭菌费用总清单如下： */
    public static String formatSettlementIntro(BillingPeriod period) {
        return "从:" + formatCnDate(period.start()) + "  至: "
                + formatCnDate(period.end()) + " 灭菌费用总清单如下：";
    }

    public static String formatCnDate(LocalDate date) {
        return date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    public static String formatClosingDate(BillingPeriod period) {
        return formatCnDate(period.end());
    }

    /**
     * 标题：{医院名}结款通知函（不含计费规则名称）。
     */
    public static String buildTitle(String hospitalName, String planName) {
        String hospital = hospitalName != null ? hospitalName.trim() : "";
        if (hospital.isEmpty()) {
            return "结款通知函";
        }
        return hospital + "结款通知函";
    }

    public static String buildClosingText(String companyName, BillingPeriod period) {
        String company = companyName != null && !companyName.isBlank()
                ? companyName.trim()
                : "黑龙江省铂康医疗灭菌有限公司";
        return company + "\n" + formatClosingDate(period);
    }

    /** 分科室汇总标题：{医院}各科室{起止日期}灭菌价格汇总 */
    public static String formatDeptSummaryTitle(String hospitalName, BillingPeriod period) {
        String hospital = hospitalName != null ? hospitalName.trim() : "医院";
        if (period == null) {
            return hospital + "各科室灭菌价格汇总";
        }
        return hospital + "各科室" + formatCnDate(period.start()) + "-"
                + formatCnDate(period.end()) + "灭菌价格汇总";
    }

    /** 替换 closingText 中首个 yyyy年M月d日 为账期末日。 */
    public static String replaceClosingDate(String closingText, BillingPeriod period) {
        if (closingText == null || closingText.isBlank() || period == null) {
            return closingText;
        }
        return closingText.replaceFirst(
                "(\\d{4})年(\\d{1,2})月(\\d{1,2})日",
                formatClosingDate(period));
    }

    private static List<LocalDate> extractDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher cn = CN_DATE.matcher(text);
        while (cn.find()) {
            dates.add(toLocalDate(cn.group(1), cn.group(2), cn.group(3)));
        }
        if (!dates.isEmpty()) {
            return dates;
        }
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            dates.add(toLocalDate(iso.group(1), iso.group(2), iso.group(3)));
        }
        return dates;
    }

    private static LocalDate toLocalDate(String y, String m, String d) {
        return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
    }
}
