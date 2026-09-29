package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内勤账单导出价表规则：空白包名/包材=通配；单价计价；系统价仅校对。
 */
@Component
public class ClerkBillPriceRuleApplier {

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "^(?:(\\d+)\\s*≤\\s*)?N\\s*(?:[>＞]\\s*(\\d+)|<\\s*(\\d+))?$", Pattern.CASE_INSENSITIVE);

    public record ApplyResult(List<BillRowItem> rows, List<String> validationWarnings) {}

    public record ClerkPriceHit(String ruleName, String ruleType, double unitPrice, JsonNode matchedRule) {}

    /** 内勤账单价规则命中结果（对账三层计价编排器使用）。 */
    public record PriceRuleMatch(JsonNode rule, double exportTotal) {}

    public Optional<PriceRuleMatch> findMatchingPriceRule(
            JsonNode compiledClerk,
            BillRowItem row,
            String customerCode,
            Set<String> disabledRuleIds) {
        if (compiledClerk == null || row == null) {
            return Optional.empty();
        }
        List<JsonNode> rules = collectPriceRules(compiledClerk);
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        rules.sort(Comparator.comparingInt(r -> r.path("priority").asInt(100)));
        for (JsonNode rule : rules) {
            if (isRuleDisabled(rule, customerCode, disabledRuleIds)) {
                continue;
            }
            if (matches(rule.path("params"), row)) {
                double exportTotal = computeExportTotal(row, rule.path("params"));
                if (exportTotal >= 0) {
                    return Optional.of(new PriceRuleMatch(rule, exportTotal));
                }
            }
        }
        return Optional.empty();
    }

    public ApplyResult apply(JsonNode compiledClerk, List<BillRowItem> rows) {
        if (compiledClerk == null || rows == null || rows.isEmpty()) {
            return new ApplyResult(rows, List.of());
        }
        List<JsonNode> rules = collectPriceRules(compiledClerk);
        if (rules.isEmpty()) {
            return new ApplyResult(rows, List.of());
        }
        rules.sort(Comparator.comparingInt(r -> r.path("priority").asInt(100)));
        List<String> warnings = new ArrayList<>();
        for (BillRowItem row : rows) {
            JsonNode matched = findMatch(rules, row);
            if (matched != null) {
                JsonNode params = matched.path("params");
                double exportTotal = computeExportTotal(row, params);
                if (exportTotal >= 0) {
                    row.setTotalPrice(exportTotal);
                    if (row.getPackCount() != null && row.getPackCount() > 0) {
                        row.setUnitPrice(exportTotal / row.getPackCount());
                    } else {
                        row.setUnitPrice(exportTotal);
                    }
                }
                maybeValidateSystemPrice(row, params, warnings);
            }
            validatePriceOnlyRules(compiledClerk, row, warnings);
        }
        return new ApplyResult(rows, warnings);
    }

    static boolean isRuleDisabled(JsonNode rule, String customerCode, Set<String> disabledRuleIds) {
        return isRuleDisabled(disabledRuleIds, customerCode, rule.path("name").asText(""));
    }

    private static List<JsonNode> collectPriceRules(JsonNode compiledClerk) {
        List<JsonNode> rules = new ArrayList<>();
        JsonNode clerkRules = compiledClerk.path("clerkRules");
        if (!clerkRules.isArray()) {
            return rules;
        }
        for (JsonNode rule : clerkRules) {
            if (!rule.path("isActive").asBoolean(true)) {
                continue;
            }
            String type = rule.path("ruleType").asText("");
            if ("BILL_EXPORT_PRICE_RULE".equals(type) || "PACK_NAME_PRICE".equals(type)) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private JsonNode findMatch(List<JsonNode> rules, BillRowItem row) {
        for (JsonNode rule : rules) {
            if (matches(rule.path("params"), row)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matches(JsonNode params, BillRowItem row) {
        if (!matchesTypes(params.path("acceptedTypes"), row.getType())) {
            return false;
        }
        if (!matchesPackName(params, row.getPackName())) {
            return false;
        }
        if (!matchesPackaging(params.path("packagingMaterial"), row.getPackageMaterial())) {
            return false;
        }
        if (!matchesInstrumentRange(params.path("instrumentCountRange").asText(null), row)) {
            return false;
        }
        return true;
    }

    private boolean matchesTypes(JsonNode acceptedTypes, String rowType) {
        if (!acceptedTypes.isArray() || acceptedTypes.isEmpty()) {
            return true;
        }
        String normalized = normalize(rowType);
        for (JsonNode t : acceptedTypes) {
            if (normalized.contains(normalize(t.asText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPackName(JsonNode params, String packName) {
        String text = packName != null ? packName : "";
        JsonNode exclude = params.path("excludePackNameKeywords");
        if (exclude.isArray() && !exclude.isEmpty()) {
            if (BillingConditionEvaluator.matchesKeywordsByMode(
                    text, exclude, BillingConditionEvaluator.KEYWORD_MATCH_CONTAINS)) {
                return false;
            }
        }
        JsonNode keywords = params.path("packNameKeywords");
        if (!keywords.isArray() || keywords.isEmpty()) {
            return true;
        }
        String matchMode = params.path("packNameMatchMode").asText(
                BillingConditionEvaluator.KEYWORD_MATCH_EXACT_TOKEN);
        return BillingConditionEvaluator.matchesKeywordsByMode(text, keywords, matchMode);
    }

    private boolean matchesPackaging(JsonNode packagingMaterial, String rowMaterial) {
        if (packagingMaterial == null || packagingMaterial.isNull()) {
            return true;
        }
        String expected = packagingMaterial.asText("").trim();
        if (expected.isEmpty()) {
            return true;
        }
        String actual = rowMaterial != null ? rowMaterial : "";
        return actual.contains(expected) || normalize(actual).contains(normalize(expected));
    }

    private boolean matchesInstrumentRange(String rangeExpr, BillRowItem row) {
        if (rangeExpr == null || rangeExpr.isBlank() || "N".equalsIgnoreCase(rangeExpr.trim())) {
            return true;
        }
        int count = instrumentCount(row);
        String normalized = rangeExpr.replace(" ", "").replace("＞", ">");
        Matcher m = RANGE_PATTERN.matcher(normalized);
        if (!m.matches()) {
            return true;
        }
        Integer lower = m.group(1) != null ? Integer.parseInt(m.group(1)) : null;
        Integer greaterThan = m.group(2) != null ? Integer.parseInt(m.group(2)) : null;
        Integer upperExclusive = m.group(3) != null ? Integer.parseInt(m.group(3)) : null;
        if (lower != null && count < lower) {
            return false;
        }
        if (greaterThan != null && count <= greaterThan) {
            return false;
        }
        if (upperExclusive != null && count >= upperExclusive) {
            return false;
        }
        return true;
    }

    private double computeExportTotal(BillRowItem row, JsonNode params) {
        if (!params.has("unitPrice") || params.path("unitPrice").isNull()) {
            return -1;
        }
        double unitPrice = params.path("unitPrice").asDouble();
        String mode = params.path("unitPriceMode").asText("FIXED");
        return switch (mode) {
            case "PER_PIECE" -> unitPrice * Math.max(1, instrumentCount(row));
            case "PER_PACK" -> unitPrice * Math.max(1, packCount(row));
            default -> unitPrice;
        };
    }

    private void validatePriceOnlyRules(JsonNode compiledClerk, BillRowItem row, List<String> warnings) {
        JsonNode clerkRules = compiledClerk.path("clerkRules");
        if (!clerkRules.isArray()) {
            return;
        }
        for (JsonNode rule : clerkRules) {
            if (!rule.path("isActive").asBoolean(true)) {
                continue;
            }
            if (!"PRICE_VALIDATE_ONLY".equals(rule.path("ruleType").asText(""))) {
                continue;
            }
            JsonNode params = rule.path("params");
            double rate = params.path("rate").asDouble(1.0);
            if (rate <= 0 || rate >= 1.0) {
                continue;
            }
            Double standard = row.getExpectedUnitPrice();
            if (standard == null || standard <= 0) {
                continue;
            }
            double expectedDiscounted = round2(standard * rate);
            Double actual = row.getUnitPrice();
            if (actual == null || actual <= 0) {
                continue;
            }
            if (Math.abs(actual - expectedDiscounted) > 0.05) {
                warnings.add(String.format(
                        "行%d 标准价七折校对: 期望%.2f(标准%.2f×%.2f) 实际%.2f (%s)",
                        row.getRowNumber() != null ? row.getRowNumber() : -1,
                        expectedDiscounted,
                        standard,
                        rate,
                        actual,
                        row.getPackName()));
            }
        }
    }

    private void maybeValidateSystemPrice(BillRowItem row, JsonNode params, List<String> warnings) {
        if (!"VALIDATE_ONLY".equals(params.path("systemPriceMode").asText("IGNORE"))) {
            return;
        }
        if (!params.has("systemPrice") || params.path("systemPrice").isNull()) {
            return;
        }
        double systemPrice = params.path("systemPrice").asDouble();
        Double actual = row.getTotalPrice();
        if (actual == null) {
            return;
        }
        if (Math.abs(actual - systemPrice) > 0.05) {
            warnings.add(String.format(
                    "行%d 系统价校对偏差: 期望%.2f 实际%.2f (%s)",
                    row.getRowNumber() != null ? row.getRowNumber() : -1,
                    systemPrice,
                    actual,
                    row.getPackName()));
        }
    }

    private static int instrumentCount(BillRowItem row) {
        if (row.getInstrumentCount() != null && row.getInstrumentCount() > 0) {
            return row.getInstrumentCount();
        }
        return packCount(row);
    }

    private static int packCount(BillRowItem row) {
        return row.getPackCount() != null && row.getPackCount() > 0 ? row.getPackCount() : 1;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 对账编排：命中首条未禁用的内勤计价规则并返回单价（不修改行对象）。
     */
    public Optional<ClerkPriceHit> tryApplyFirstMatch(
            JsonNode compiledClerk,
            BillRowItem row,
            Set<String> disabledRuleIds,
            String customerCode) {
        if (compiledClerk == null || row == null) {
            return Optional.empty();
        }
        List<JsonNode> rules = collectPriceRules(compiledClerk);
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        rules.sort(Comparator.comparingInt(r -> r.path("priority").asInt(100)));
        for (JsonNode rule : rules) {
            String ruleName = rule.path("name").asText("");
            if (isRuleDisabled(disabledRuleIds, customerCode, ruleName)) {
                continue;
            }
            if (!matches(rule.path("params"), row)) {
                continue;
            }
            JsonNode params = rule.path("params");
            double exportTotal = computeExportTotal(row, params);
            if (exportTotal < 0) {
                continue;
            }
            int packCount = packCount(row);
            double unitPrice = packCount > 0 ? round2(exportTotal / packCount) : round2(exportTotal);
            return Optional.of(new ClerkPriceHit(
                    ruleName,
                    rule.path("ruleType").asText(""),
                    unitPrice,
                    rule));
        }
        return Optional.empty();
    }

    public static boolean isRuleDisabled(Set<String> disabledRuleIds, String customerCode, String ruleName) {
        if (disabledRuleIds == null || disabledRuleIds.isEmpty() || ruleName == null || ruleName.isBlank()) {
            return false;
        }
        if (customerCode != null && disabledRuleIds.contains(customerCode + ":" + ruleName)) {
            return true;
        }
        return disabledRuleIds.contains(ruleName);
    }
}
