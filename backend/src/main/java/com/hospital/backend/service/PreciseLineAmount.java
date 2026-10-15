package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 单价展示收到分，总价用未收分单价乘包数后再收到分。
 * 只有两种算法结果不同时才留下未收分单价，整分一口价不会被多出来的小数改掉。
 */
public final class PreciseLineAmount {

    public static final String NOTE_KEY = "unroundedUnitPrice";

    private PreciseLineAmount() {
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal multiplyRate(double baseUnit, double rate) {
        return of(baseUnit).multiply(of(rate));
    }

    public static double round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double round2(double value) {
        return round2(of(value));
    }

    public static double total(BigDecimal preciseUnit, int packCount) {
        int packs = Math.max(1, packCount);
        return round2(preciseUnit.multiply(BigDecimal.valueOf(packs)));
    }

    public static double total(double preciseUnit, int packCount) {
        return total(of(preciseUnit), packCount);
    }

    /**
     * 先把单价收到分再乘包数，和先乘包数再收分，结果不同时返回未收分单价。
     * 已经是整分时返回 null，调用方继续用展示单价。
     */
    public static Double keepIfTotalDiffers(BigDecimal preciseUnit, int packCount) {
        double display = round2(preciseUnit);
        double fromDisplay = total(display, packCount);
        double fromPrecise = total(preciseUnit, packCount);
        if (Math.abs(fromDisplay - fromPrecise) <= 0.001) {
            return null;
        }
        return preciseUnit.stripTrailingZeros().doubleValue();
    }

    public static double correctedTotal(Double displayUnit, Double unroundedUnit, int packCount) {
        double basis = unroundedUnit != null ? unroundedUnit : (displayUnit != null ? displayUnit : 0);
        return total(basis, packCount);
    }

    public static Double readNote(String billingNotesJson) {
        if (billingNotesJson == null || billingNotesJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(billingNotesJson);
            return readNode(root.get(NOTE_KEY));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Double readNote(Map<String, Object> billingNotes) {
        if (billingNotes == null || billingNotes.isEmpty()) {
            return null;
        }
        return readBoxed(billingNotes.get(NOTE_KEY));
    }

    private static Double readNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double readBoxed(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
