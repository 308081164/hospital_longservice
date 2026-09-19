package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内勤账单导出价表规则：空白包名/包材=通配；单价计价；系统价仅校对。
 */
@Component
public class ClerkBillPriceRuleApplier {

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "^(?:(\\d+)\\s*≤\\s*)?N\\s*(?:<\\s*(\\d+))?$", Pattern.CASE_INSENSITIVE);

    public record ApplyResult(List<BillRowItem> rows, List<String> validationWarnings) {}

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
            if (matched == null) {
                continue;
            }
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
        return new ApplyResult(rows, warnings);
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
        if (exclude.isArray()) {
            for (JsonNode kw : exclude) {
                if (text.contains(kw.asText())) {
                    return false;
                }
            }
        }
        JsonNode keywords = params.path("packNameKeywords");
        if (!keywords.isArray() || keywords.isEmpty()) {
            return true;
        }
        for (JsonNode kw : keywords) {
            if (text.contains(kw.asText())) {
                return true;
            }
        }
        return false;
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
        Matcher m = RANGE_PATTERN.matcher(rangeExpr.replace(" ", ""));
        if (!m.matches()) {
            return true;
        }
        Integer lower = m.group(1) != null ? Integer.parseInt(m.group(1)) : null;
        Integer upper = m.group(2) != null ? Integer.parseInt(m.group(2)) : null;
        if (lower != null && count < lower) {
            return false;
        }
        if (upper != null && count >= upper) {
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
}
