package com.hospital.backend.service;

import com.hospital.backend.entity.HospitalReconciliationJob;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves billing month as {@code YYYY-MM} for settlement extras, logistics cards, etc.
 */
public final class BillingMonthResolver {

    private static final Pattern ISO_PREFIX = Pattern.compile("^(\\d{4})-(\\d{2})");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");
    private static final Pattern CN_FULL_MONTH = Pattern.compile("(\\d{4})年(\\d{1,2})月");
    private static final Pattern CN_MONTH_ONLY = Pattern.compile("(\\d{1,2})月");
    private static final Pattern CN_DATE = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");

    private BillingMonthResolver() {
    }

    public static String resolve(HospitalReconciliationJob job) {
        if (job == null) {
            return null;
        }
        String fromFile = resolveFromFileName(job.getSourceFileName(), job.getCreatedAt());
        if (fromFile != null) {
            return fromFile;
        }
        String fromRange = resolveFromDateRange(job.getSourceDateRange());
        if (fromRange != null) {
            return fromRange;
        }
        if (job.getCreatedAt() != null) {
            return job.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return null;
    }

    static String resolveFromFileName(String fileName, LocalDateTime createdAt) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Matcher full = CN_FULL_MONTH.matcher(fileName);
        if (full.find()) {
            return formatMonth(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)));
        }
        Matcher monthOnly = CN_MONTH_ONLY.matcher(fileName);
        if (monthOnly.find()) {
            int month = Integer.parseInt(monthOnly.group(1));
            int year = createdAt != null ? createdAt.getYear() : LocalDateTime.now().getYear();
            return formatMonth(year, month);
        }
        return null;
    }

    static String resolveFromDateRange(String sourceDateRange) {
        if (sourceDateRange == null || sourceDateRange.isBlank()) {
            return null;
        }
        String trimmed = sourceDateRange.trim();
        Matcher isoPrefix = ISO_PREFIX.matcher(trimmed);
        if (isoPrefix.find()) {
            return isoPrefix.group(1) + "-" + isoPrefix.group(2);
        }

        java.util.List<DateParts> dates = new java.util.ArrayList<>();
        Matcher cnDate = CN_DATE.matcher(trimmed);
        while (cnDate.find()) {
            dates.add(new DateParts(
                    Integer.parseInt(cnDate.group(1)),
                    Integer.parseInt(cnDate.group(2)),
                    Integer.parseInt(cnDate.group(3))));
        }
        Matcher isoDate = ISO_DATE.matcher(trimmed);
        while (isoDate.find()) {
            dates.add(new DateParts(
                    Integer.parseInt(isoDate.group(1)),
                    Integer.parseInt(isoDate.group(2)),
                    Integer.parseInt(isoDate.group(3))));
        }
        if (dates.size() >= 2) {
            DateParts start = dates.get(0);
            DateParts end = dates.get(dates.size() - 1);
            if (isMidMonthBillingPeriod(start, end)) {
                return formatMonth(start.year(), start.month());
            }
            return formatMonth(end.year(), end.month());
        }
        if (dates.size() == 1) {
            DateParts only = dates.get(0);
            return formatMonth(only.year(), only.month());
        }

        Matcher cnMonth = CN_FULL_MONTH.matcher(trimmed);
        if (cnMonth.find()) {
            return formatMonth(Integer.parseInt(cnMonth.group(1)), Integer.parseInt(cnMonth.group(2)));
        }
        return null;
    }

    /** 15 日–次月 14 日账期：结款/override 按起始月（如 6.15–7.14 → 2026-06）。 */
    private static boolean isMidMonthBillingPeriod(DateParts start, DateParts end) {
        if (start.year() == end.year() && start.month() == end.month()) {
            return false;
        }
        return start.day() >= 15 && end.day() <= 14;
    }

    private record DateParts(int year, int month, int day) {
    }

    private static String formatMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return null;
        }
        return String.format("%04d-%02d", year, month);
    }
}
