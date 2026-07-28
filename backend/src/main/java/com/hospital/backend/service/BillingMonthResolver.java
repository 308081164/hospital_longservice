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
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-]\\d{1,2}");
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

        // Cross-month ranges: use the last date in the range (billing month = period end).
        Matcher cnDate = CN_DATE.matcher(trimmed);
        int lastYear = -1;
        int lastMonth = -1;
        while (cnDate.find()) {
            lastYear = Integer.parseInt(cnDate.group(1));
            lastMonth = Integer.parseInt(cnDate.group(2));
        }
        if (lastYear > 0 && lastMonth > 0) {
            return formatMonth(lastYear, lastMonth);
        }

        Matcher cnMonth = CN_FULL_MONTH.matcher(trimmed);
        if (cnMonth.find()) {
            return formatMonth(Integer.parseInt(cnMonth.group(1)), Integer.parseInt(cnMonth.group(2)));
        }

        Matcher isoDate = ISO_DATE.matcher(trimmed);
        lastYear = -1;
        lastMonth = -1;
        while (isoDate.find()) {
            lastYear = Integer.parseInt(isoDate.group(1));
            lastMonth = Integer.parseInt(isoDate.group(2));
        }
        if (lastYear > 0 && lastMonth > 0) {
            return formatMonth(lastYear, lastMonth);
        }
        return null;
    }

    private static String formatMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return null;
        }
        return String.format("%04d-%02d", year, month);
    }
}
