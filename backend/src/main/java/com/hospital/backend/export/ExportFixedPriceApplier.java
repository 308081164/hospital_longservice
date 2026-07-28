package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.service.BillingConditionEvaluator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出阶段固定价应用器 —— 将客户 FIXED_PRICE 规则作用于账单行（S8 波次3：stable Job 行价与 seed 不同步时对齐处理后表）。
 * <p>
 * 默认信任 Job 已写入的 {@link BillRowItem#getCorrectedTotalPrice()}；仅 {@code exportApply=true} 的规则会覆盖。
 */
@Component
public class ExportFixedPriceApplier {

    public List<BillRowItem> apply(JsonNode compiledRules, List<BillRowItem> rows) {
        if (compiledRules == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        JsonNode fixedPrices = compiledRules.path("specialRules").path("fixedPrices");
        if (!fixedPrices.isArray() || fixedPrices.isEmpty()) {
            return rows;
        }

        List<BillRowItem> result = new ArrayList<>(rows.size());
        for (BillRowItem row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                result.add(row);
                continue;
            }
            result.add(applyToRow(row, fixedPrices));
        }
        return result;
    }

    private BillRowItem applyToRow(BillRowItem row, JsonNode fixedPrices) {
        String combined = safe(row.getType()) + safe(row.getPackName()) + safe(row.getPackageMaterial());
        String department = resolveDepartment(row);
        int billingPieces = resolveBillingPieces(row);

        for (JsonNode rule : fixedPrices) {
            if (!rule.path("exportApply").asBoolean(false)
                    && row.getCorrectedTotalPrice() != null) {
                continue;
            }
            if (!matchesKeywords(combined, rule.path("keywords"))) {
                continue;
            }
            if (matchesAnyKeyword(combined, rule.path("excludeKeywords"))) {
                continue;
            }
            if (!BillingConditionEvaluator.departmentMatches(rule, department)) {
                continue;
            }
            if (!BillingConditionEvaluator.instrumentCountInRange(rule, billingPieces)) {
                continue;
            }
            double price = rule.path("price").asDouble(Double.NaN);
            if (Double.isNaN(price)) {
                continue;
            }
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            double total;
            if (rule.path("pricePerInstrument").asBoolean(false)) {
                int instruments = row.getInstrumentCount() != null ? Math.max(1, row.getInstrumentCount()) : packCount;
                total = round(price * instruments);
                row.setUnitPrice(round(total / packCount));
            } else {
                row.setUnitPrice(price);
                total = round(price * packCount);
            }
            row.setExpectedUnitPrice(row.getUnitPrice());
            row.setTotalPrice(total);
            row.setCorrectedTotalPrice(total);
            row.setDifference(0.0);
            break;
        }
        return row;
    }

    private String resolveDepartment(BillRowItem row) {
        if (row.getSheetName() != null && !row.getSheetName().isBlank()) {
            return row.getSheetName().trim();
        }
        if (row.getOriginal() != null) {
            Object dept = row.getOriginal().get("department");
            if (dept != null && !String.valueOf(dept).isBlank()) {
                return String.valueOf(dept).trim();
            }
        }
        return "";
    }

    private boolean matchesKeywords(String text, JsonNode keywords) {
        if (!keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        for (JsonNode kw : keywords) {
            String s = kw.asText("").trim();
            if (!s.isEmpty() && text.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyKeyword(String text, JsonNode keywords) {
        if (!keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        for (JsonNode kw : keywords) {
            String s = kw.asText("").trim();
            if (!s.isEmpty() && text.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private int resolveBillingPieces(BillRowItem row) {
        int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 1;
        int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
        return Math.max(1, (int) Math.round((double) instrumentCount / packCount));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
