package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.service.BillingConditionEvaluator;
import com.hospital.backend.service.BillingPolicyApplier;
import com.hospital.backend.service.ClerkBillPriceRuleApplier;
import com.hospital.backend.service.PreciseLineAmount;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 导出阶段折扣应用器 —— 原价导入、折扣导出（P4-08 / FR-M2-03 / FR-M2-05）。
 */
@Component
public class ExportStageDiscountApplier {

    public record ClerkDiscountHit(
            String ruleName,
            double unitPriceBefore,
            double unitPriceAfter,
            Double unroundedUnitPrice,
            JsonNode policy,
            String stepNote) {}

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
        double importUnit = row.getUnitPrice() != null ? row.getUnitPrice() : 0;
        Double originalImport = resolveOriginalImportUnit(row);
        Double baseUnit = row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : importUnit;
        if (originalImport != null && originalImport > 0) {
            baseUnit = originalImport;
        }
        if (baseUnit == null || baseUnit <= 0) {
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
            if (!discountTypeAllowed(policy, row.getType())) {
                continue;
            }
            JsonNode params = policy.path("params");
            if (params.path("validateOnly").asBoolean(false)) {
                continue;
            }
            if (params.path("skipWhenClerkFixedPrice").asBoolean(false)) {
                String pricingRule = row.getPricingRule();
                if (pricingRule != null && pricingRule.startsWith("内勤计价")) {
                    continue;
                }
            }
            if (discountAlreadyApplied(row, policy)) {
                continue;
            }
            boolean skipAlreadyDiscounted = params.path("skipWhenAlreadyDiscounted").asBoolean(false);
            if (!skipAlreadyDiscounted && importUnit > baseUnit + 0.02) {
                baseUnit = importUnit;
            }
            if (skipAlreadyDiscounted) {
                double correctedUnit = resolveEffectiveUnitPrice(row, billingPieces);
                double expectedDiscounted = resolveExpectedDiscountedUnit(baseUnit, billingPieces, params);
                if (billingPieces > 1) {
                    Double overridePrice = resolveFixedPriceOverride(params, baseUnit);
                    if (overridePrice != null) {
                        expectedDiscounted = overridePrice;
                    }
                }
                if (Math.abs(importUnit - expectedDiscounted) <= 0.02
                        || Math.abs(correctedUnit - expectedDiscounted) <= 0.02) {
                    continue;
                }
                if (Math.abs(importUnit - correctedUnit) <= 0.02 && importUnit > 0) {
                    continue;
                }
                if (originalImport != null && originalImport > 0) {
                    if (importUnit > originalImport + 0.02) {
                        applyExportPrice(row, originalImport, importPriceNote(policy, baseUnit, originalImport));
                        break;
                    }
                    if (importUnit < originalImport - 0.02
                            && Math.abs(originalImport - expectedDiscounted) <= 0.02) {
                        applyExportPrice(row, originalImport, importPriceNote(policy, baseUnit, originalImport));
                        break;
                    }
                }
            }
            double skipWhenUnit = params.path("skipWhenUnitPriceEquals").asDouble(Double.NaN);
            if (!Double.isNaN(skipWhenUnit) && Math.abs(baseUnit - skipWhenUnit) <= 0.02) {
                continue;
            }
            List<BillingPolicyApplier.PieceTierDiscount> tiers =
                    BillingPolicyApplier.parsePieceTierDiscounts(params);
            double discounted;
            String ruleName = policy.path("name").asText("客户折扣");
            String formulaClause;
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            row.setUnroundedUnitPrice(null);
            if (!tiers.isEmpty()) {
                BillingPolicyApplier.PieceTierDiscount matched =
                        BillingPolicyApplier.findMatchedPieceTier(tiers, billingPieces, baseUnit);
                discounted = BillingPolicyApplier.applyPieceTierRate(baseUnit, billingPieces, tiers);
                formulaClause = tierFormula(ruleName, baseUnit, matched, discounted);
            } else {
                double rate = params.path("rate").asDouble(Double.NaN);
                if (Double.isNaN(rate) || rate <= 0 || rate >= 1.0) {
                    continue;
                }
                BigDecimal precise = PreciseLineAmount.multiplyRate(baseUnit, rate);
                discounted = PreciseLineAmount.round2(precise);
                row.setUnroundedUnitPrice(PreciseLineAmount.keepIfTotalDiffers(precise, packCount));
                formulaClause = BillingPolicyApplier.rateFormula(ruleName, baseUnit, rate, discounted);
            }

            if (billingPieces > 1) {
                Double overridePrice = resolveFixedPriceOverride(params, baseUnit);
                if (overridePrice != null) {
                    discounted = overridePrice;
                    row.setUnroundedUnitPrice(null);
                    formulaClause = ruleName + " 固定单价 " + String.format("%.2f", discounted);
                }
            }
            double afterFormula = discounted;
            double capped = BillingPolicyApplier.capUnitPrice(afterFormula, params, row.getType());
            Double capShown = null;
            if (capped + 0.001 < afterFormula) {
                discounted = capped;
                row.setUnroundedUnitPrice(null);
                capShown = params.path("maxUnitPrice").asDouble(capped);
            }

            row.setUnitPrice(discounted);
            row.setExpectedUnitPrice(discounted);
            applyExportPrice(row, discounted, BillingPolicyApplier.formatExportDiscountNote(
                    ruleName, baseUnit, formulaClause, capShown, afterFormula));
            break;
        }
        return row;
    }

    private static String tierFormula(
            String ruleName,
            double before,
            BillingPolicyApplier.PieceTierDiscount matched,
            double after) {
        if (matched != null && matched.fixedUnitPrice() != null) {
            return ruleName + " 固定单价 " + String.format("%.2f", after);
        }
        double rate = matched == null ? 1.0 : matched.rate();
        return BillingPolicyApplier.rateFormula(ruleName, before, rate, after);
    }

    private static String importPriceNote(JsonNode policy, double before, double importPrice) {
        String ruleName = policy.path("name").asText("客户折扣");
        return "导出阶段折扣：" + ruleName + "，折前 " + String.format("%.2f", before)
                + "，" + ruleName + " 按导入原价 " + String.format("%.2f", importPrice);
    }

    private void applyExportPrice(BillRowItem row, double discounted, String stepNote) {
        int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
        double total = PreciseLineAmount.correctedTotal(discounted, row.getUnroundedUnitPrice(), packCount);
        row.setTotalPrice(total);
        row.setCorrectedTotalPrice(total);
        row.setDifference(0.0);

        List<String> notes = row.getNotes() != null ? new ArrayList<>(row.getNotes()) : new ArrayList<>();
        notes.add(stepNote);
        row.setNotes(notes);
    }

    /** 对账编排已把折后价写入规则单价并记下备注时，导出不再乘第二次。 */
    private boolean discountAlreadyApplied(BillRowItem row, JsonNode policy) {
        if (row.getNotes() == null || row.getNotes().isEmpty()) {
            return false;
        }
        String name = policy.path("name").asText("").trim();
        if (name.isEmpty()) {
            return false;
        }
        String marker = "导出阶段折扣：" + name;
        for (String note : row.getNotes()) {
            if (note != null && note.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private Double resolveOriginalImportUnit(BillRowItem row) {
        if (row.getOriginal() != null && row.getOriginal().get("importUnitPrice") instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private double resolveExpectedDiscountedUnit(double baseUnit, int billingPieces, JsonNode params) {
        List<BillingPolicyApplier.PieceTierDiscount> tiers =
                BillingPolicyApplier.parsePieceTierDiscounts(params);
        if (!tiers.isEmpty()) {
            return BillingPolicyApplier.applyPieceTierRate(baseUnit, billingPieces, tiers);
        }
        double rate = params.path("rate").asDouble(Double.NaN);
        if (Double.isNaN(rate) || rate <= 0 || rate >= 1.0) {
            return baseUnit;
        }
        return BillingPolicyApplier.round(baseUnit * rate);
    }

    private double resolveEffectiveUnitPrice(BillRowItem row, int billingPieces) {
        if (row.getCorrectedTotalPrice() != null && billingPieces > 0) {
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            return row.getCorrectedTotalPrice() / packCount;
        }
        if (row.getUnitPrice() != null) {
            return row.getUnitPrice();
        }
        return row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : 0;
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

    /** 折扣可以写在策略根、scope 或 params.acceptedTypes。没写类型则不过滤。 */
    private static boolean discountTypeAllowed(JsonNode policy, String rowType) {
        JsonNode params = policy.path("params");
        if (params.path("acceptedTypes").isArray() && !params.path("acceptedTypes").isEmpty()) {
            return BillingConditionEvaluator.packTypeMatches(params, rowType);
        }
        boolean policyTyped = (policy.path("acceptedTypes").isArray() && !policy.path("acceptedTypes").isEmpty())
                || (policy.path("scope").path("acceptedTypes").isArray()
                && !policy.path("scope").path("acceptedTypes").isEmpty());
        if (policyTyped) {
            return BillingConditionEvaluator.packTypeMatches(policy, rowType);
        }
        return true;
    }

    /**
     * 对账编排：在已有基价上应用首条未禁用的 bill_export 折扣策略。
     */
    public Optional<ClerkDiscountHit> tryApplyDiscount(
            JsonNode compiledRules,
            BillRowItem row,
            Set<String> disabledRuleIds,
            String customerCode) {
        if (compiledRules == null || row == null) {
            return Optional.empty();
        }
        List<JsonNode> exportPolicies = BillingPolicyApplier.findPoliciesByStage(
                compiledRules, "DISCOUNT", BillingPolicyApplier.STAGE_EXPORT_ONLY);
        if (exportPolicies.isEmpty()) {
            return Optional.empty();
        }
        double before = row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : 0;
        if (before <= 0) {
            return Optional.empty();
        }
        BillRowItem working = copyRow(row);
        working.setExpectedUnitPrice(before);
        working.setUnitPrice(before);
        for (JsonNode policy : exportPolicies) {
            String policyName = policy.path("name").asText("");
            if (ClerkBillPriceRuleApplier.isRuleDisabled(disabledRuleIds, customerCode, policyName)) {
                continue;
            }
            if (policy.path("params").path("validateOnly").asBoolean(false)) {
                continue;
            }
            BillRowItem discounted = applyToRow(working, List.of(policy));
            double after = discounted.getExpectedUnitPrice() != null
                    ? discounted.getExpectedUnitPrice()
                    : discounted.getUnitPrice() != null ? discounted.getUnitPrice() : before;
            if (Math.abs(after - before) > 0.001) {
                return Optional.of(new ClerkDiscountHit(
                        policyName, before, after, discounted.getUnroundedUnitPrice(), policy,
                        exportDiscountNote(discounted, policyName)));
            }
        }
        return Optional.empty();
    }

    private static String exportDiscountNote(BillRowItem row, String ruleName) {
        if (row.getNotes() != null) {
            String marker = "导出阶段折扣：" + (ruleName == null ? "" : ruleName);
            for (int i = row.getNotes().size() - 1; i >= 0; i--) {
                String note = row.getNotes().get(i);
                if (note != null && note.startsWith(marker)) {
                    return note;
                }
            }
        }
        return "导出阶段折扣：" + (ruleName == null || ruleName.isBlank() ? "客户折扣" : ruleName);
    }

    private static BillRowItem copyRow(BillRowItem source) {
        BillRowItem copy = new BillRowItem();
        copy.setType(source.getType());
        copy.setPackName(source.getPackName());
        copy.setPackageMaterial(source.getPackageMaterial());
        copy.setPackCount(source.getPackCount());
        copy.setInstrumentCount(source.getInstrumentCount());
        copy.setUnitPrice(source.getUnitPrice());
        copy.setTotalPrice(source.getTotalPrice());
        copy.setExpectedUnitPrice(source.getExpectedUnitPrice());
        copy.setUnroundedUnitPrice(source.getUnroundedUnitPrice());
        copy.setCorrectedTotalPrice(source.getCorrectedTotalPrice());
        copy.setNotes(source.getNotes() != null ? new ArrayList<>(source.getNotes()) : null);
        copy.setOriginal(source.getOriginal());
        return copy;
    }
}
