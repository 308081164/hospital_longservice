package com.hospital.backend.util;

import com.hospital.backend.entity.Product;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalizes customer product rule display names.
 * Legacy migrations stored names like "黑龙江省第二医院（松北区）3.6空心钉工具包固定单价".
 */
public final class ProductRuleNameUtils {

    private static final String[] LEGACY_RULE_TYPE_SUFFIXES = {
            "固定单价", "固定价格", "按件计价", "倍率计价", "倍率", "件数折算", "折算", "场景加收", "加收"
    };

    private static final Pattern PER_ITEM_PRICE_SUFFIX = Pattern.compile("\\s*每件\\s*[\\d.]+\\s*元\\s*$");

    private static final Pattern HOSPITAL_PREFIX = Pattern.compile(
            "^[\\u4e00-\\u9fff\\d\\s.-]+?(?:医院|门诊|集团|中心|诊所)(?:[（(][^）)]+[）)])?\\s*(.+)$");

    private static final Pattern GROUP_PREFIX = Pattern.compile("^[\\u4e00-\\u9fff]+集团\\s+");

    private ProductRuleNameUtils() {
    }

    public static String sanitizeLegacyRuleName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String text = name.trim();
        text = PER_ITEM_PRICE_SUFFIX.matcher(text).replaceAll("").trim();

        for (String suffix : LEGACY_RULE_TYPE_SUFFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length()).trim();
                break;
            }
        }

        var hospitalMatch = HOSPITAL_PREFIX.matcher(text);
        if (hospitalMatch.matches()) {
            text = hospitalMatch.group(1).trim();
        }

        text = GROUP_PREFIX.matcher(text).replaceAll("").trim();
        return text.isBlank() ? null : text;
    }

    public static boolean isLegacyPollutedRuleName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String sanitized = sanitizeLegacyRuleName(name.trim());
        return sanitized != null && !sanitized.equals(name.trim());
    }

    public static String resolveProductRuleName(String requestName, Product product, List<String> keywords) {
        if (product != null && product.getName() != null && !product.getName().isBlank()) {
            return product.getName().trim();
        }
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank()) {
                    return keyword.trim();
                }
            }
        }
        if (requestName != null && !requestName.isBlank()) {
            String trimmed = requestName.trim();
            if (!isLegacyPollutedRuleName(trimmed)) {
                return trimmed;
            }
            String sanitized = sanitizeLegacyRuleName(trimmed);
            if (sanitized != null && !sanitized.isBlank()) {
                return sanitized;
            }
        }
        return null;
    }
}
