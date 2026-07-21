package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 PricingEngine 抽出的规则条件评估（TD-02 部分偿还）。
 */
public final class BillingConditionEvaluator {

    private BillingConditionEvaluator() {
    }

    public static boolean hospitalMatches(JsonNode rule, String hospitalName) {
        JsonNode hospitals = rule.path("hospitals");
        if (!hospitals.isArray() || hospitals.isEmpty()) {
            return true;
        }
        if (hospitalName == null || hospitalName.isBlank()) {
            return false;
        }
        for (JsonNode h : hospitals) {
            if (hospitalName.contains(h.asText())) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesKeywords(String text, JsonNode keywords) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        if (text == null) {
            text = "";
        }
        for (JsonNode kw : keywords) {
            String keyword = kw.asText("");
            if (!keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesRuleKeywords(String text, JsonNode keywords) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return true;
        }
        return matchesKeywords(text, keywords);
    }

    public static boolean bagSizeMatches(JsonNode rule, int bagSize) {
        if (rule.has("bagSizeEquals")) {
            return bagSize == rule.path("bagSizeEquals").asInt(-1);
        }
        if (rule.has("maxBagSizeExclusive")) {
            return bagSize < rule.path("maxBagSizeExclusive").asInt(Integer.MAX_VALUE);
        }
        return true;
    }

    public static String resolveRowTemperature(String combined) {
        if (combined == null) {
            return "HT";
        }
        if (combined.contains("低温") || combined.contains("ETO") || combined.contains("EO")) {
            return "LT";
        }
        return "HT";
    }

    public static boolean temperatureScopeMatches(String scope, String rowTemperature) {
        if (scope == null || scope.isBlank() || "ANY".equalsIgnoreCase(scope)) {
            return true;
        }
        return scope.equalsIgnoreCase(rowTemperature);
    }

    /**
     * 产品/变体绑定匹配：规则含 variantId 时必须精确匹配变体；仅含 productId 时匹配产品即可。
     */
    public static boolean matchesProductBinding(
            JsonNode rule,
            Long matchedProductId,
            Long matchedVariantId,
            String combinedText) {
        if (rule.has("variantId") && !rule.path("variantId").isNull()) {
            long ruleVariantId = rule.path("variantId").asLong();
            return matchedVariantId != null && matchedVariantId == ruleVariantId;
        }
        if (rule.has("productId") && !rule.path("productId").isNull()) {
            long ruleProductId = rule.path("productId").asLong();
            if (matchedProductId != null && matchedProductId == ruleProductId) {
                return true;
            }
            return matchesRuleKeywords(combinedText, rule.path("keywords"));
        }
        return matchesRuleKeywords(combinedText, rule.path("keywords"));
    }

    public static boolean instrumentCountInRange(JsonNode rule, int effectiveCount) {
        int minCount = rule.path("minInstrumentCount").asInt(Integer.MIN_VALUE);
        int maxCount = rule.path("maxInstrumentCount").asInt(Integer.MAX_VALUE);
        return effectiveCount >= minCount && effectiveCount <= maxCount;
    }

    /**
     * 生成规则匹配签名，用于冲突检测（CFG-05）。
     */
    public static String matchSignature(Map<String, Object> rule) {
        return String.join("|",
                str(rule, "ruleType"),
                str(rule, "productId"),
                str(rule, "variantId"),
                str(rule, "keywords"),
                str(rule, "temperature"),
                str(rule, "bagSizeEquals"),
                str(rule, "maxBagSizeExclusive"),
                str(rule, "minInstrumentCount"),
                str(rule, "maxInstrumentCount"));
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public record RowContext(
            String type,
            String packName,
            String packageMaterial,
            String hospitalName,
            String department,
            Double unitPrice,
            int bagSize,
            int effectiveCount,
            Long matchedProductId,
            Long matchedVariantId,
            String combinedText
    ) {
        public static RowContext fromRow(
                Map<String, Object> row,
                int bagSize,
                int effectiveCount,
                Long matchedProductId) {
            return fromRow(row, bagSize, effectiveCount, matchedProductId, null);
        }

        public static RowContext fromRow(
                Map<String, Object> row,
                int bagSize,
                int effectiveCount,
                Long matchedProductId,
                Long matchedVariantId) {
            String type = str(row, "type");
            String packName = str(row, "packName");
            String packageMaterial = str(row, "packageMaterial");
            String combined = type + " " + packName + " " + packageMaterial;
            String department = str(row, "department");
            if (department.isBlank()) {
                department = str(row, "sheetName");
            }
            return new RowContext(
                    type, packName, packageMaterial,
                    str(row, "hospitalName"),
                    department,
                    doubleOrNull(row, "unitPrice"),
                    bagSize, effectiveCount, matchedProductId, matchedVariantId, combined);
        }

        private static String str(Map<String, Object> row, String key) {
            Object v = row.get(key);
            return v == null ? "" : String.valueOf(v).trim();
        }

        private static Double doubleOrNull(Map<String, Object> row, String key) {
            Object v = row.get(key);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            return null;
        }
    }

    public static boolean matchesRule(JsonNode rule, RowContext ctx) {
        if (!hospitalMatches(rule, ctx.hospitalName())) {
            return false;
        }
        JsonNode excludeKeywords = rule.path("excludeKeywords");
        if (excludeKeywords.isArray() && !excludeKeywords.isEmpty()
                && matchesKeywords(ctx.combinedText(), excludeKeywords)) {
            return false;
        }
        if (!matchesProductBinding(rule, ctx.matchedProductId(), ctx.matchedVariantId(), ctx.combinedText())) {
            return false;
        }
        JsonNode materials = rule.path("materials");
        if (materials.isArray() && !materials.isEmpty()
                && !matchesKeywords(ctx.combinedText(), materials)) {
            return false;
        }
        if (!bagSizeMatches(rule, ctx.bagSize())) {
            return false;
        }
        if (!temperatureScopeMatches(rule.path("temperature").asText("ANY"), resolveRowTemperature(ctx.combinedText()))) {
            return false;
        }
        if (!instrumentCountInRange(rule, ctx.effectiveCount())) {
            return false;
        }
        if (!originalUnitPriceMatches(rule, ctx.unitPrice())) {
            return false;
        }
        return departmentMatches(rule, ctx.department());
    }

    public static boolean originalUnitPriceMatches(JsonNode rule, Double unitPrice) {
        if (!rule.has("originalUnitPrice")) {
            return true;
        }
        if (unitPrice == null) {
            return false;
        }
        return Math.abs(unitPrice - rule.path("originalUnitPrice").asDouble()) <= 0.001;
    }

    public static boolean departmentMatches(JsonNode rule, String rowDepartment) {
        JsonNode departments = rule.path("departments");
        if (departments.isArray() && !departments.isEmpty()) {
            return matchesDepartmentList(departments, rowDepartment);
        }
        JsonNode conditions = rule.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode cond : conditions) {
                if ("department".equalsIgnoreCase(cond.path("field").asText())) {
                    return matchesDepartmentList(cond.path("value"), rowDepartment);
                }
            }
        }
        return true;
    }

    private static boolean matchesDepartmentList(JsonNode departments, String rowDepartment) {
        if (rowDepartment == null || rowDepartment.isBlank()) {
            return false;
        }
        String normalizedRow = rowDepartment.replaceAll("\\s+", "");
        for (JsonNode dept : departments) {
            String candidate = dept.asText("").replaceAll("\\s+", "");
            if (!candidate.isEmpty() && (normalizedRow.equals(candidate)
                    || normalizedRow.contains(candidate) || candidate.contains(normalizedRow))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> parseDepartmentList(String conditionsJson) {
        List<String> departments = new ArrayList<>();
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return departments;
        }
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(conditionsJson);
            if (node.isArray()) {
                for (JsonNode cond : node) {
                    if ("department".equalsIgnoreCase(cond.path("field").asText())) {
                        JsonNode value = cond.path("value");
                        if (value.isArray()) {
                            value.forEach(v -> departments.add(v.asText()));
                        } else if (value.isTextual()) {
                            departments.add(value.asText());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return departments;
    }
}
