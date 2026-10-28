package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对账三层计价编排：内勤一口价 &gt; 客服计价（特色/标准）&gt; 内勤导出折扣。
 * 内勤一口价是最终单价，不再乘折扣。特色价和标准价都乘内勤折扣，不受规则 skipDiscount 影响。
 */
@Component
@RequiredArgsConstructor
public class ReconciliationPricingOrchestrator {

    public static final String CATEGORY_CLERK_PRICE = "clerk_price";
    public static final String CATEGORY_CLERK_DISCOUNT = "clerk_discount";

    private static final Pattern DISCOUNT_NOTE_PATTERN =
            Pattern.compile("^导出阶段折扣：(.+?)，");

    private final ClerkBillPriceRuleApplier clerkBillPriceRuleApplier;
    private final ExportStageDiscountApplier exportStageDiscountApplier;

    public PricingEngine.ProcessedResult processRow(
            Map<String, Object> rowMap,
            PricingEngine engine,
            JsonNode clerkCompiled,
            String customerCode,
            Set<String> disabledCategories) {
        List<String> calculationSteps = new ArrayList<>();
        BillRowItem clerkRow = toBillRowItem(rowMap);
        boolean clerkPriceDisabled = isCategoryDisabled(disabledCategories, CATEGORY_CLERK_PRICE);
        boolean clerkDiscountDisabled = isCategoryDisabled(disabledCategories, CATEGORY_CLERK_DISCOUNT);

        Optional<ClerkBillPriceRuleApplier.PriceRuleMatch> clerkPrice = Optional.empty();
        if (!clerkPriceDisabled && clerkCompiled != null && customerCode != null) {
            clerkPrice = clerkBillPriceRuleApplier.findMatchingPriceRule(
                    clerkCompiled, clerkRow, customerCode, Set.of());
        }
        PricingEngine.ProcessedResult result;
        String pricingLayer;
        String clerkRuleName = null;
        Double priceBeforeDiscount = null;

        if (clerkPrice.isPresent()) {
            ClerkBillPriceRuleApplier.PriceRuleMatch match = clerkPrice.get();
            clerkRuleName = match.rule().path("name").asText("");
            int packCount = packCount(rowMap);
            double unitPrice = round2(match.exportTotal() / Math.max(1, packCount));
            result = new PricingEngine.ProcessedResult();
            result.expectedUnitPrice = unitPrice;
            result.pricingRule = "内勤计价：" + clerkRuleName;
            result.pricingPath = "clerk";
            result.status = "unchanged";
            result.notes = new ArrayList<>();
            result.notes.add(String.format("内勤账单价规则「%s」，单价 %.2f", clerkRuleName, unitPrice));
            attachFieldValidation(result, rowMap);
            syncClerkQuotedPrice(result, rowMap);
            pricingLayer = "clerk";
            calculationSteps.add("内勤计价命中：" + clerkRuleName);
            priceBeforeDiscount = unitPrice;
        } else {
            result = engine.processRow(rowMap);
            pricingLayer = resolveEngineLayer(result);
            if (result.expectedUnitPrice != null) {
                priceBeforeDiscount = result.expectedUnitPrice;
            }
            calculationSteps.add("客服计价：" + ("special".equals(pricingLayer) ? "特色" : "标准"));
        }

        String clerkDiscountRuleName = null;
        Double priceAfterDiscount = result.expectedUnitPrice;
        if (!clerkDiscountDisabled
                && clerkPrice.isEmpty()
                && clerkCompiled != null
                && result.expectedUnitPrice != null
                && result.expectedUnitPrice > 0) {
            double preDiscountUnit = result.expectedUnitPrice;
            BillRowItem discountRow = toBillRowItem(rowMap);
            discountRow.setOriginal(null);
            discountRow.setExpectedUnitPrice(preDiscountUnit);
            discountRow.setUnitPrice(preDiscountUnit);
            Optional<ExportStageDiscountApplier.ClerkDiscountHit> hit =
                    exportStageDiscountApplier.tryApplyDiscount(
                            clerkCompiled, discountRow, Set.of(), customerCode);
            if (hit.isPresent()
                    && Math.abs(hit.get().unitPriceAfter() - preDiscountUnit) > 0.001) {
                ExportStageDiscountApplier.ClerkDiscountHit applied = hit.get();
                clerkDiscountRuleName = applied.ruleName();
                priceAfterDiscount = applied.unitPriceAfter();
                result.expectedUnitPrice = priceAfterDiscount;
                result.unroundedUnitPrice = applied.unroundedUnitPrice();
                String ruleLabel = clerkDiscountRuleName != null ? clerkDiscountRuleName : "客户折扣";
                String stepNote = applied.stepNote();
                if (stepNote == null || stepNote.isBlank()) {
                    stepNote = String.format(
                            "导出阶段折扣：%s，折前 %.2f，%s 单价 %.2f",
                            ruleLabel, preDiscountUnit, ruleLabel, priceAfterDiscount);
                }
                calculationSteps.add(stepNote.startsWith("导出阶段折扣：")
                        ? "内勤折扣：" + stepNote.substring("导出阶段折扣：".length())
                        : "内勤折扣：" + stepNote);
                if (result.notes == null) {
                    result.notes = new ArrayList<>();
                }
                result.notes.add(stepNote);
                syncCorrectionTotals(result, rowMap);
            }
        }

        rewriteFinalPriceNote(result, rowMap);
        Map<String, Object> billingNotes = result.billingNotes != null
                ? new LinkedHashMap<>(result.billingNotes)
                : new LinkedHashMap<>();
        billingNotes.put("pricingLayer", pricingLayer);
        if (clerkRuleName != null) {
            billingNotes.put("clerkRuleName", clerkRuleName);
        }
        if (clerkDiscountRuleName != null) {
            billingNotes.put("clerkDiscountRuleName", clerkDiscountRuleName);
        }
        if (priceBeforeDiscount != null) {
            billingNotes.put("priceBeforeDiscount", priceBeforeDiscount);
        }
        if (priceAfterDiscount != null) {
            billingNotes.put("priceAfterDiscount", priceAfterDiscount);
        }
        if (result.unroundedUnitPrice != null) {
            billingNotes.put(PreciseLineAmount.NOTE_KEY, result.unroundedUnitPrice);
        }
        billingNotes.put("calculationSteps", calculationSteps);
        appendDisabledCategoryMessages(billingNotes, disabledCategories);
        result.billingNotes = billingNotes;
        return result;
    }

    static boolean isCategoryDisabled(Set<String> disabledCategories, String category) {
        return disabledCategories != null && disabledCategories.contains(category);
    }

    public static Set<String> parseDisabledCategories(String pricingRuleOverridesJson) {
        if (pricingRuleOverridesJson == null || pricingRuleOverridesJson.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(pricingRuleOverridesJson);
            JsonNode categories = root.path("disabledCategories");
            if (!categories.isArray()) {
                return Set.of();
            }
            Set<String> result = new LinkedHashSet<>();
            for (JsonNode category : categories) {
                String text = category.asText("").trim();
                if (CATEGORY_CLERK_PRICE.equals(text) || CATEGORY_CLERK_DISCOUNT.equals(text)) {
                    result.add(text);
                }
            }
            return result;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    public static String serializeDisabledCategories(Set<String> disabledCategories) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode array = root.putArray("disabledCategories");
        if (disabledCategories != null) {
            disabledCategories.stream().sorted().forEach(array::add);
        }
        return root.toString();
    }

    private static String resolveEngineLayer(PricingEngine.ProcessedResult result) {
        if (result == null) {
            return "standard";
        }
        if ("standard".equals(result.pricingPath)) {
            return "standard";
        }
        if (result.matchedRuleId != null) {
            return "special";
        }
        if ("fixed".equals(result.pricingPath)) {
            return "special";
        }
        String rule = result.pricingRule != null ? result.pricingRule : "";
        if (rule.contains("高温") || rule.contains("低温") || rule.contains("敷料")
                || rule.contains("阶梯") || rule.contains("标准")) {
            return "standard";
        }
        return "special";
    }

    private static BillRowItem toBillRowItem(Map<String, Object> rowMap) {
        BillRowItem item = new BillRowItem();
        item.setType(stringVal(rowMap, "type"));
        item.setPackName(stringVal(rowMap, "packName"));
        item.setPackageMaterial(stringVal(rowMap, "packageMaterial"));
        item.setPackCount(intVal(rowMap, "packCount", 1));
        item.setInstrumentCount(intVal(rowMap, "instrumentCount", 0));
        item.setUnitPrice(doubleVal(rowMap, "unitPrice"));
        item.setTotalPrice(doubleVal(rowMap, "totalPrice"));
        item.setExpectedUnitPrice(doubleVal(rowMap, "expectedUnitPrice"));
        item.setRowNumber(intVal(rowMap, "rowNumber", 0));
        item.setStatus(stringVal(rowMap, "status"));
        return item;
    }

    private static void appendDisabledCategoryMessages(
            Map<String, Object> billingNotes,
            Set<String> disabledCategories) {
        if (disabledCategories == null || disabledCategories.isEmpty()) {
            return;
        }
        List<String> labels = new ArrayList<>();
        if (disabledCategories.contains(CATEGORY_CLERK_PRICE)) {
            labels.add("内勤计价");
        }
        if (disabledCategories.contains(CATEGORY_CLERK_DISCOUNT)) {
            labels.add("内勤折扣");
        }
        if (!labels.isEmpty()) {
            billingNotes.put("disabledCategories", new ArrayList<>(disabledCategories));
            billingNotes.put("disabledClerkLayers", labels);
        }
    }

    private static String extractDiscountRuleName(BillRowItem row) {
        if (row.getNotes() == null) {
            return null;
        }
        for (String note : row.getNotes()) {
            Matcher matcher = DISCOUNT_NOTE_PATTERN.matcher(note);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private static void attachFieldValidation(PricingEngine.ProcessedResult result, Map<String, Object> rowMap) {
        String type = stringVal(rowMap, "type");
        String packName = stringVal(rowMap, "packName");
        String material = stringVal(rowMap, "packageMaterial");
        int instrumentCount = intVal(rowMap, "instrumentCount", 0);
        int packs = packCount(rowMap);
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(type, packName, material, instrumentCount, packs);
        if (violations.isEmpty()) {
            return;
        }
        if (result.notes == null) {
            result.notes = new ArrayList<>();
        }
        for (BillRowFieldConsistencyValidator.Violation violation : violations) {
            result.notes.add("【字段核对】" + violation.message());
        }
        Map<String, Object> fieldNotes = BillRowFieldConsistencyValidator.toBillingNotes(violations);
        if (result.billingNotes == null) {
            result.billingNotes = new LinkedHashMap<>();
        }
        if (fieldNotes != null) {
            result.billingNotes.put("fieldConsistency", fieldNotes);
        }
    }

    private static boolean hasFieldValidationIssue(PricingEngine.ProcessedResult result) {
        if (result.notes == null) {
            return false;
        }
        for (String note : result.notes) {
            if (note != null && note.startsWith("【字段核对】") && !note.startsWith("【字段核对】规则单价")) {
                return true;
            }
        }
        return false;
    }

    private static void rewriteFinalPriceNote(PricingEngine.ProcessedResult result, Map<String, Object> rowMap) {
        if (result.notes == null) {
            result.notes = new ArrayList<>();
        }
        result.notes.removeIf(note -> note != null && note.startsWith("【字段核对】规则单价"));
        Double bill = doubleVal(rowMap, "unitPrice");
        Double expected = result.expectedUnitPrice;
        if (bill == null || expected == null || Math.abs(expected - bill) <= 0.001) {
            return;
        }
        result.notes.add(String.format("【字段核对】规则单价 %.2f 元与原账单价 %.2f 元不一致，请人工复核。", expected, bill));
    }

    private static int packCount(Map<String, Object> rowMap) {
        Object raw = rowMap.get("packCount");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }

    private static String stringVal(Map<String, Object> rowMap, String key) {
        Object raw = rowMap.get(key);
        return raw == null ? null : String.valueOf(raw);
    }

    private static int intVal(Map<String, Object> rowMap, String key, int defaultValue) {
        Object raw = rowMap.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static Double doubleVal(Map<String, Object> rowMap, String key) {
        Object raw = rowMap.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 内勤一口价和账单单价不一致时标 warning，并写出修正总价和差额。
     * 与账单一致才是无需修改。
     */
    private static void syncClerkQuotedPrice(PricingEngine.ProcessedResult result, Map<String, Object> rowMap) {
        if (result.expectedUnitPrice == null) {
            return;
        }
        int packs = Math.max(intVal(rowMap, "packCount", 1), 1);
        double correctedTotal = PreciseLineAmount.correctedTotal(
                result.expectedUnitPrice, result.unroundedUnitPrice, packs);
        result.correctedTotalPrice = correctedTotal;
        Double totalPrice = doubleVal(rowMap, "totalPrice");
        double originalTotal = totalPrice != null ? totalPrice : 0.0;
        result.difference = round2(correctedTotal - originalTotal);
        Double bill = doubleVal(rowMap, "unitPrice");
        boolean unitDiffers = bill != null && Math.abs(result.expectedUnitPrice - bill) > 0.001;
        boolean totalDiffers = Math.abs(result.difference) > 0.001;
        if (unitDiffers || totalDiffers || hasFieldValidationIssue(result)) {
            result.status = "warning";
        } else {
            result.status = "unchanged";
        }
    }

    /** 内勤折扣改写规则单价后，同步修正总价/差额/状态（与 applyBatchCorrection 口径一致）。 */
    private static void syncCorrectionTotals(PricingEngine.ProcessedResult result, Map<String, Object> rowMap) {
        if (result.expectedUnitPrice == null) {
            return;
        }
        int packs = Math.max(intVal(rowMap, "packCount", 1), 1);
        double correctedTotal = PreciseLineAmount.correctedTotal(
                result.expectedUnitPrice, result.unroundedUnitPrice, packs);
        result.correctedTotalPrice = correctedTotal;
        Double totalPrice = doubleVal(rowMap, "totalPrice");
        double originalTotal = totalPrice != null ? totalPrice : 0.0;
        result.difference = round2(correctedTotal - originalTotal);
        boolean keepFieldWarning = hasFieldValidationIssue(result);
        if (Math.abs(result.difference) > 0.001) {
            result.status = keepFieldWarning ? "warning" : "corrected";
        } else if (!keepFieldWarning) {
            result.status = "unchanged";
        }
    }
}
