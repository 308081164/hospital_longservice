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

    /** 关键词匹配模式：严格对齐（名称严格对应，仅对包名做精确 token 边界匹配）。 */
    public static final String KEYWORD_MATCH_EXACT_TOKEN = "exact_token";

    /** 关键词匹配模式：含有关键词即可触发（宽松子串包含，缺省默认）。 */
    public static final String KEYWORD_MATCH_CONTAINS = "contains";

    /**
     * 读取规则上的 keywordMatchMode 字段，缺省或非法值回退为 contains。
     * 特殊收费 Excel 中「包名称带X」为包含语义；2026-08-27 基线引擎对固定价/加收类
     * 规则关键词采用组合文本子串包含，仅显式配置 exact_token 的「名称严格对应」规则走精确匹配。
     * 折算（FOLD）规则门控请使用带默认值的重载并传入 exact_token（基线行为）。
     */
    public static String resolveKeywordMatchMode(JsonNode rule) {
        return resolveKeywordMatchMode(rule, KEYWORD_MATCH_CONTAINS);
    }

    /**
     * 读取规则上的 keywordMatchMode 字段，缺省或非法值回退为 defaultMode。
     * 折算（FOLD）规则门控使用 exact_token 默认值：2026-08-27 基线即按包名分词匹配，
     * 使"针"类模式词命中"针5盒1"而不误伤"车针排/持针器"等长名称。
     */
    public static String resolveKeywordMatchMode(JsonNode rule, String defaultMode) {
        if (rule == null) {
            return defaultMode;
        }
        String mode = rule.path("keywordMatchMode").asText(defaultMode);
        if (KEYWORD_MATCH_EXACT_TOKEN.equalsIgnoreCase(mode)) {
            return KEYWORD_MATCH_EXACT_TOKEN;
        }
        if (KEYWORD_MATCH_CONTAINS.equalsIgnoreCase(mode)) {
            return KEYWORD_MATCH_CONTAINS;
        }
        return defaultMode;
    }

    /**
     * 按匹配模式判定文本是否命中关键词。
     * exact_token：关键词须作为独立 token 出现（串首/串尾或两侧为非中文字符）。
     * contains：文本包含关键词即可触发。
     * <p>单个关键词可用后缀覆盖默认模式：{@code 车针@contains} 强制宽松包含，
     * {@code 车针@exact}（或 @exact_token）强制精确 token 边界；不带后缀沿用 defaultMode。
     */
    public static boolean matchesKeywordsByMode(String text, JsonNode keywords, String defaultMode) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        String normalizedText = normalizeMatchText(text).toLowerCase();
        for (JsonNode kw : keywords) {
            for (ParsedKeyword pk : parseKeywordList(kw.asText(""))) {
                if (pk.keyword().isBlank()) {
                    continue;
                }
                String mode = pk.mode() != null ? pk.mode() : defaultMode;
                boolean hit = KEYWORD_MATCH_CONTAINS.equalsIgnoreCase(mode)
                        ? normalizedText.contains(normalizeMatchText(pk.keyword()).toLowerCase())
                        : matchesKeywordExactToken(text, pk.keyword());
                if (hit) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 按匹配模式查找首个命中关键词及其位置（供小件折算区分大小件混合包）。
     * 逐词支持 @contains / @exact 后缀覆盖；命中优先返回更长的关键词。
     */
    public static ExactTokenKeywordMatch findKeywordByMode(String text, JsonNode keywords, String defaultMode) {
        if (text == null || keywords == null || !keywords.isArray()) {
            return null;
        }
        String compact = normalizeMatchText(text);
        String compactLower = compact.toLowerCase();
        List<ParsedKeyword> parsed = new ArrayList<>();
        for (JsonNode kw : keywords) {
            parsed.addAll(parseKeywordList(kw.asText("")));
        }
        parsed.removeIf(pk -> pk.keyword().isBlank());
        parsed.sort((a, b) -> Integer.compare(b.keyword().length(), a.keyword().length()));
        for (ParsedKeyword pk : parsed) {
            String mode = pk.mode() != null ? pk.mode() : defaultMode;
            String kwLower = normalizeMatchText(pk.keyword()).toLowerCase();
            if (KEYWORD_MATCH_CONTAINS.equalsIgnoreCase(mode)) {
                int idx = compactLower.indexOf(kwLower);
                if (idx >= 0) {
                    return new ExactTokenKeywordMatch(
                            compact.substring(idx, idx + kwLower.length()), idx, compact);
                }
            } else {
                int idx = 0;
                while ((idx = compactLower.indexOf(kwLower, idx)) != -1) {
                    char prev = idx > 0 ? compactLower.charAt(idx - 1) : 0;
                    char next = idx + kwLower.length() < compactLower.length()
                            ? compactLower.charAt(idx + kwLower.length()) : 0;
                    if (isKeywordTokenBoundary(prev) && isKeywordTokenBoundary(next)) {
                        return new ExactTokenKeywordMatch(
                                compact.substring(idx, idx + kwLower.length()), idx, compact);
                    }
                    idx += kwLower.length();
                }
            }
        }
        return null;
    }

    /** 解析后的单个关键词及其（可选）词级匹配模式。 */
    public record ParsedKeyword(String keyword, String mode) {}

    /**
     * 将逗号分隔的关键词串解析为词级模式列表。
     * 语法：{@code 词}、{@code 词@contains}、{@code 词@exact}、{@code 词@exact_token}。
     * 未识别的 @ 后缀原样保留，避免误伤含 @ 的普通关键词。
     */
    public static List<ParsedKeyword> parseKeywordList(String raw) {
        List<ParsedKeyword> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("[，,]")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int at = trimmed.lastIndexOf('@');
            if (at > 0 && at < trimmed.length() - 1) {
                String suffix = trimmed.substring(at + 1).trim().toLowerCase();
                if (KEYWORD_MATCH_CONTAINS.equals(suffix)) {
                    result.add(new ParsedKeyword(trimmed.substring(0, at).trim(), KEYWORD_MATCH_CONTAINS));
                    continue;
                }
                if ("exact".equals(suffix) || "exact_token".equals(suffix) || "exacttoken".equals(suffix)) {
                    result.add(new ParsedKeyword(trimmed.substring(0, at).trim(), KEYWORD_MATCH_EXACT_TOKEN));
                    continue;
                }
            }
            result.add(new ParsedKeyword(trimmed, null));
        }
        return result;
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

    /** 规则匹配前统一全角括号与空白，避免账单半角括号导致关键词未命中。 */
    public static String normalizeMatchText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replaceAll("\\s+", "");
    }

    public static boolean matchesKeywords(String text, JsonNode keywords) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        String normalizedText = normalizeMatchText(text).toLowerCase();
        for (JsonNode kw : keywords) {
            String keyword = normalizeMatchText(kw.asText("")).toLowerCase();
            if (!keyword.isBlank() && normalizedText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCjkChar(char ch) {
        return (ch >= 0x4e00 && ch <= 0x9fff) || (ch >= 0x3400 && ch <= 0x4dbf);
    }

    /** 关键词 token 边界：串首/串尾，或两侧为非 CJK 字符（如 - / 数字）。 */
    public static boolean isKeywordTokenBoundary(char ch) {
        return ch == 0 || !isCjkChar(ch);
    }

    /**
     * 小件关键词精准匹配：关键词须作为独立 token 出现，不可嵌在其他中文词内
     * （如「针」不可命中「克氏针」，「车针」不可命中「正畸去胶车针」）。
     */
    public static boolean matchesKeywordExactToken(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        String normalized = normalizeMatchText(text).toLowerCase();
        String raw = normalizeMatchText(keyword).toLowerCase();
        if (raw.isBlank()) {
            return false;
        }
        for (String part : raw.split("[，,]")) {
            if (part.isBlank()) {
                continue;
            }
            int idx = 0;
            while ((idx = normalized.indexOf(part, idx)) != -1) {
                char prev = idx > 0 ? normalized.charAt(idx - 1) : 0;
                char next = idx + part.length() < normalized.length()
                        ? normalized.charAt(idx + part.length()) : 0;
                if (isKeywordTokenBoundary(prev) && isKeywordTokenBoundary(next)) {
                    return true;
                }
                idx += part.length();
            }
        }
        return false;
    }

    public static boolean matchesKeywordsExactToken(String text, JsonNode keywords) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return false;
        }
        for (JsonNode kw : keywords) {
            if (matchesKeywordExactToken(text, kw.asText(""))) {
                return true;
            }
        }
        return false;
    }

    public record ExactTokenKeywordMatch(String keyword, int position, String compactText) {}

    /** 按关键词长度降序，返回首个精准 token 命中（优先「穿刺针」而非「针」）。 */
    public static ExactTokenKeywordMatch findLongestExactTokenKeyword(String text, JsonNode keywords) {
        if (text == null || keywords == null || !keywords.isArray()) {
            return null;
        }
        String compact = normalizeMatchText(text);
        String compactLower = compact.toLowerCase();
        List<String> parts = new ArrayList<>();
        for (JsonNode kw : keywords) {
            String raw = normalizeMatchText(kw.asText("")).toLowerCase();
            if (raw.isBlank()) {
                continue;
            }
            for (String part : raw.split("[，,]")) {
                if (!part.isBlank()) {
                    parts.add(part);
                }
            }
        }
        parts.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String part : parts) {
            int idx = 0;
            while ((idx = compactLower.indexOf(part, idx)) != -1) {
                char prev = idx > 0 ? compactLower.charAt(idx - 1) : 0;
                char next = idx + part.length() < compactLower.length()
                        ? compactLower.charAt(idx + part.length()) : 0;
                if (isKeywordTokenBoundary(prev) && isKeywordTokenBoundary(next)) {
                    return new ExactTokenKeywordMatch(
                            compact.substring(idx, idx + part.length()), idx, compact);
                }
                idx += part.length();
            }
        }
        return null;
    }

    /** 按关键词长度降序返回首个「包含」命中的关键词（contains 模式，不做 token 边界校验）。 */
    public static ExactTokenKeywordMatch findLongestContainsKeyword(String text, JsonNode keywords) {
        if (text == null || keywords == null || !keywords.isArray()) {
            return null;
        }
        String compact = normalizeMatchText(text);
        String compactLower = compact.toLowerCase();
        List<String> parts = new ArrayList<>();
        for (JsonNode kw : keywords) {
            String raw = normalizeMatchText(kw.asText("")).toLowerCase();
            if (raw.isBlank()) {
                continue;
            }
            for (String part : raw.split("[，,]")) {
                if (!part.isBlank()) {
                    parts.add(part);
                }
            }
        }
        parts.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String part : parts) {
            int idx = compactLower.indexOf(part);
            if (idx >= 0) {
                return new ExactTokenKeywordMatch(
                        compact.substring(idx, idx + part.length()), idx, compact);
            }
        }
        return null;
    }

    public static boolean matchesRuleKeywords(String text, JsonNode keywords) {
        return matchesRuleKeywords(text, keywords, KEYWORD_MATCH_CONTAINS);
    }

    /**
     * 规则条件关键词匹配：支持词级 {@code @contains}/{@code @exact} 后缀及规则级 keywordMatchMode。
     * 无关键词时视为通过（与历史 matchesRuleKeywords 语义一致）。
     */
    public static boolean matchesRuleKeywords(String text, JsonNode keywords, String defaultMode) {
        if (keywords == null || !keywords.isArray() || keywords.isEmpty()) {
            return true;
        }
        String mode = defaultMode != null && !defaultMode.isBlank()
                ? defaultMode
                : KEYWORD_MATCH_CONTAINS;
        return matchesKeywordsByMode(text, keywords, mode);
    }

    public static boolean matchesRuleKeywords(JsonNode rule, String text) {
        if (rule == null) {
            return true;
        }
        return matchesRuleKeywords(text, rule.path("keywords"), resolveKeywordMatchMode(rule));
    }

    /**
     * 规则条件关键词匹配（按匹配模式选择文本范围）：
     * exact_token（名称严格对应）仅对包名判定——组合文本中袋型/材质紧邻包名会导致
     * 关键词右侧永远邻接中文而失配；contains（包含）在组合文本上判定（与 2026-08-27 基线一致）。
     */
    public static boolean matchesRuleKeywords(JsonNode rule, String packName, String combinedText) {
        if (rule == null) {
            return true;
        }
        String mode = resolveKeywordMatchMode(rule);
        String text = KEYWORD_MATCH_EXACT_TOKEN.equals(mode) ? packName : combinedText;
        return matchesRuleKeywords(text, rule.path("keywords"), mode);
    }

    public static boolean bagSizeMatches(JsonNode rule, int bagSize) {
        if (rule.has("bagSizeEquals") && bagSize != rule.path("bagSizeEquals").asInt(-1)) {
            return false;
        }
        if (rule.has("minBagSizeInclusive") && bagSize < rule.path("minBagSizeInclusive").asInt()) {
            return false;
        }
        if (rule.has("maxBagSizeInclusive") && bagSize > rule.path("maxBagSizeInclusive").asInt()) {
            return false;
        }
        if (rule.has("maxBagSizeExclusive") && bagSize >= rule.path("maxBagSizeExclusive").asInt()) {
            return false;
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
     * 关键词兜底按规则匹配模式选择文本范围（exact_token 对包名，contains 对组合文本）。
     */
    public static boolean matchesProductBinding(
            JsonNode rule,
            Long matchedProductId,
            Long matchedVariantId,
            String packName,
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
            return matchesRuleKeywords(rule, packName, combinedText);
        }
        return matchesRuleKeywords(rule, packName, combinedText);
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
                && matchesKeywordsByMode(ctx.combinedText(), excludeKeywords, resolveKeywordMatchMode(rule))) {
            return false;
        }
        if (!matchesProductBinding(rule, ctx.matchedProductId(), ctx.matchedVariantId(), ctx.packName(), ctx.combinedText())) {
            return false;
        }
        JsonNode materials = rule.path("materials");
        if (materials.isArray() && !materials.isEmpty()
                && !matchesKeywordsByMode(ctx.combinedText(), materials, resolveKeywordMatchMode(rule))) {
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
