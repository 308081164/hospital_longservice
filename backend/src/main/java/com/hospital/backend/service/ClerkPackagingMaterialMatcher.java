package com.hospital.backend.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内勤价表包材档位（如 {@code 25cm}）与账单「高温纸塑袋300*320」对齐，口径与 {@link PricingEngine} 纸塑尺寸识别一致。
 */
final class ClerkPackagingMaterialMatcher {

    private static final int[] STANDARD_BAG_CM = {10, 15, 20, 25};
    private static final Pattern DIMENSION = Pattern.compile("(\\d+)\\s*[*×xX]\\s*\\d+");
    private static final Pattern CM_SUFFIX = Pattern.compile("(\\d+)\\s*cm", Pattern.CASE_INSENSITIVE);

    private ClerkPackagingMaterialMatcher() {
    }

    static boolean matches(String expectedRuleMaterial, String rowMaterial) {
        if (expectedRuleMaterial == null || expectedRuleMaterial.isBlank()) {
            return true;
        }
        String expected = expectedRuleMaterial.trim();
        String actual = rowMaterial != null ? rowMaterial : "";
        if (actual.contains(expected)) {
            return true;
        }
        String normExpected = normalize(expected);
        String normActual = normalize(actual);
        if (normActual.contains(normExpected)) {
            return true;
        }
        Integer expectedCm = parseCmSuffix(expected);
        if (expectedCm != null) {
            int tier = resolvePaperPlasticCmTier(actual);
            if (tier > 0 && tier == expectedCm) {
                return true;
            }
        }
        return false;
    }

    static int resolvePaperPlasticCmTier(String material) {
        if (material == null || material.isBlank()) {
            return 0;
        }
        String key = normalize(material);
        Matcher mm = DIMENSION.matcher(key);
        if (mm.find()) {
            int firstNum = Integer.parseInt(mm.group(1));
            if (firstNum >= 50) {
                int cmSize = firstNum / 10;
                int exact = findExactBagSize(cmSize);
                if (exact > 0) {
                    return exact;
                }
                int ceiling = findCeilingBagSize(cmSize);
                if (ceiling > 0) {
                    return ceiling;
                }
                return STANDARD_BAG_CM[STANDARD_BAG_CM.length - 1];
            }
        }
        Matcher cm = CM_SUFFIX.matcher(key);
        if (cm.find()) {
            return Integer.parseInt(cm.group(1));
        }
        return 0;
    }

    private static Integer parseCmSuffix(String text) {
        Matcher m = CM_SUFFIX.matcher(normalize(text));
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static int findExactBagSize(int cmSize) {
        for (int size : STANDARD_BAG_CM) {
            if (size == cmSize) {
                return size;
            }
        }
        return 0;
    }

    private static int findCeilingBagSize(int cmSize) {
        for (int size : STANDARD_BAG_CM) {
            if (size >= cmSize) {
                return size;
            }
        }
        return 0;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
