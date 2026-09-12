package com.hospital.backend.service;

import com.hospital.backend.entity.HospitalReconciliationRow;

import java.util.Map;

/**
 * 对账「仅异常」筛选与异常导出统一口径。
 */
public final class ReconciliationAnomalyDetector {

    private static final double PRICE_TOLERANCE = 0.001;

    private ReconciliationAnomalyDetector() {
    }

    public static boolean isAnomalyRow(HospitalReconciliationRow row, boolean includeFieldConsistency) {
        if (row == null) {
            return false;
        }
        String status = row.getStatus() != null ? row.getStatus().trim() : "";
        if ("warning".equals(status) || "corrected".equals(status)) {
            return true;
        }
        if (hasUnitPriceMismatch(row.getUnitPrice(), row.getExpectedUnitPrice())) {
            return true;
        }
        if (needsManualRuleReview(row.getPricingRule(), row.getExpectedUnitPrice())) {
            return true;
        }
        Double difference = row.getDifference();
        if (difference != null && Math.abs(difference) > PRICE_TOLERANCE) {
            return true;
        }
        return includeFieldConsistency
                && BillRowBillingNotesSupport.hasAnyFieldCheckViolations(row.getBillingNotes());
    }

    public static boolean isAnomalyMap(Map<String, Object> row, boolean includeFieldConsistency) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        String status = valueToString(row.get("status"));
        if ("warning".equals(status) || "corrected".equals(status)) {
            return true;
        }
        Double unitPrice = toDouble(row.get("unitPrice"));
        Double expectedUnitPrice = toDouble(row.get("expectedUnitPrice"));
        if (hasUnitPriceMismatch(unitPrice, expectedUnitPrice)) {
            return true;
        }
        if (needsManualRuleReview(valueToString(row.get("pricingRule")), expectedUnitPrice)) {
            return true;
        }
        Double difference = toDouble(row.get("difference"));
        if (difference != null && Math.abs(difference) > PRICE_TOLERANCE) {
            return true;
        }
        if (!includeFieldConsistency) {
            return false;
        }
        Object billingNotes = row.get("billingNotes");
        if (billingNotes == null) {
            billingNotes = row.get("billing_notes");
        }
        if (billingNotes instanceof String json) {
            return BillRowBillingNotesSupport.hasAnyFieldCheckViolations(json);
        }
        return false;
    }

    public static boolean hasUnitPriceMismatch(Double unitPrice, Double expectedUnitPrice) {
        if (unitPrice == null || expectedUnitPrice == null) {
            return false;
        }
        return Math.abs(unitPrice - expectedUnitPrice) > PRICE_TOLERANCE;
    }

    public static boolean needsManualRuleReview(String pricingRule, Double expectedUnitPrice) {
        String rule = pricingRule != null ? pricingRule.trim() : "";
        if ("未命中规则".equals(rule)) {
            return true;
        }
        if (rule.contains("未识别包装类型")) {
            return true;
        }
        if (rule.contains("special_only 未命中")) {
            return true;
        }
        return rule.isEmpty() && expectedUnitPrice == null;
    }

    private static String valueToString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
