package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.BillingPolicyApplier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出阶段折扣应用器 —— 原价导入、折扣导出（P4-08 / FR-M2-03 / FR-M2-05）。
 */
@Component
public class ExportStageDiscountApplier {

    public List<BillRowItem> apply(JsonNode compiledRules, List<BillRowItem> rows) {
        if (compiledRules == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        List<JsonNode> exportPolicies = BillingPolicyApplier.findPoliciesByStage(
                compiledRules, "DISCOUNT", BillingPolicyApplier.STAGE_EXPORT_ONLY);
        if (exportPolicies.isEmpty()) {
            return rows;
        }

        List<BillRowItem> result = new ArrayList<>(rows.size());
        for (BillRowItem row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                result.add(row);
                continue;
            }
            result.add(applyToRow(row, exportPolicies));
        }
        return result;
    }

    private BillRowItem applyToRow(BillRowItem row, List<JsonNode> exportPolicies) {
        Double baseUnit = row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : row.getUnitPrice();
        if (baseUnit == null) {
            return row;
        }

        int billingPieces = resolveBillingPieces(row);
        String combined = safe(row.getType()) + safe(row.getPackName()) + safe(row.getPackageMaterial());
        String rowTemp = BillingConditionEvaluator.resolveRowTemperature(combined);

        for (JsonNode policy : exportPolicies) {
            String scopeTemp = policy.path("scope").path("temperature").asText("ANY");
            if (!BillingConditionEvaluator.temperatureScopeMatches(scopeTemp, rowTemp)) {
                continue;
            }
            JsonNode params = policy.path("params");
            List<BillingPolicyApplier.PieceTierDiscount> tiers =
                    BillingPolicyApplier.parsePieceTierDiscounts(params);
            double discounted;
            if (!tiers.isEmpty()) {
                discounted = BillingPolicyApplier.applyPieceTierRate(baseUnit, billingPieces, tiers);
            } else {
                double rate = params.path("rate").asDouble(Double.NaN);
                if (Double.isNaN(rate) || rate <= 0 || rate >= 1.0) {
                    continue;
                }
                discounted = BillingPolicyApplier.round(baseUnit * rate);
            }

            if (billingPieces > 1) {
                Double overridePrice = resolveFixedPriceOverride(params, baseUnit);
                if (overridePrice != null) {
                    discounted = overridePrice;
                }
            }

            row.setUnitPrice(discounted);
            row.setExpectedUnitPrice(discounted);
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            double total = BillingPolicyApplier.round(discounted * packCount);
            row.setTotalPrice(total);
            row.setCorrectedTotalPrice(total);
            row.setDifference(0.0);

            List<String> notes = row.getNotes() != null ? new ArrayList<>(row.getNotes()) : new ArrayList<>();
            notes.add("导出阶段折扣：" + policy.path("name").asText("客户折扣")
                    + "，单价 " + String.format("%.2f", baseUnit) + " → " + String.format("%.2f", discounted));
            row.setNotes(notes);
            break;
        }
        return row;
    }

    private Double resolveFixedPriceOverride(JsonNode params, double baseUnit) {
        JsonNode overrides = params.path("fixedPriceOverrides");
        if (!overrides.isArray()) {
            return null;
        }
        for (JsonNode override : overrides) {
            double from = override.path("from").asDouble(Double.NaN);
            double to = override.path("to").asDouble(Double.NaN);
            if (!Double.isNaN(from) && !Double.isNaN(to) && Math.abs(baseUnit - from) <= 0.001) {
                return to;
            }
        }
        return null;
    }

    private int resolveBillingPieces(BillRowItem row) {
        int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 1;
        int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
        return Math.max(1, (int) Math.round((double) instrumentCount / packCount));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
