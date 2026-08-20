package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FOLD 规则不可整除时拆分为多行（P4-03 / FR-M3-04）。
 */
public final class RowSplitter {

    private RowSplitter() {
    }

    public record SplitSegment(int instrumentCount, int billingPieces) {
    }

    /**
     * 若行命中 FOLD 规则且器械数无法整除 foldRatio，则拆分为多行；否则返回单行。
     */
    public static List<Map<String, Object>> expandRow(Map<String, Object> row, JsonNode rules) {
        int instrumentCount = intVal(row, "instrumentCount");
        int packCount = Math.max(1, intVal(row, "packCount"));
        int perPackCount = packCount > 1
                ? Math.max(1, (int) Math.round((double) instrumentCount / packCount))
                : instrumentCount;
        if (perPackCount <= 0) {
            perPackCount = 1;
        }

        JsonNode foldRule = findMatchingFoldRule(row, rules, perPackCount);
        if (foldRule == null) {
            return List.of(row);
        }

        int threshold = foldRule.path("threshold").asInt(5);
        double foldRatio = foldRule.path("foldRatio").asDouble(5.0);
        int ratio = (int) Math.max(1, Math.round(foldRatio));

        if (perPackCount <= threshold || perPackCount % ratio == 0) {
            return List.of(row);
        }

        List<SplitSegment> segments = splitInstrumentCount(perPackCount, ratio, threshold);
        if (segments.size() <= 1) {
            return List.of(row);
        }

        List<Map<String, Object>> expanded = new ArrayList<>();
        int splitIndex = 0;
        for (SplitSegment segment : segments) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            int segmentTotalInstruments = segment.instrumentCount() * packCount;
            copy.put("instrumentCount", segmentTotalInstruments);
            copy.put("splitFromRow", row.get("rowNumber"));
            copy.put("splitIndex", ++splitIndex);
            copy.put("splitTotal", segments.size());
            copy.put("splitBillingPieces", segment.billingPieces());
            expanded.add(copy);
        }
        return expanded;
    }

    static List<SplitSegment> splitInstrumentCount(int count, int foldRatio, int threshold) {
        List<SplitSegment> segments = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            if (threshold > 0 && remaining <= threshold) {
                segments.add(new SplitSegment(remaining, 1));
                break;
            }
            if (remaining % foldRatio == 0) {
                segments.add(new SplitSegment(remaining, remaining / foldRatio));
                break;
            }
            if (remaining > foldRatio) {
                int chunk = (remaining / foldRatio) * foldRatio;
                segments.add(new SplitSegment(chunk, chunk / foldRatio));
                remaining -= chunk;
            } else {
                segments.add(new SplitSegment(remaining, 1));
                break;
            }
        }
        return segments;
    }

    private static JsonNode findMatchingFoldRule(Map<String, Object> row, JsonNode rules, int effectiveCount) {
        String combined = str(row, "type") + " " + str(row, "packName") + " " + str(row, "packageMaterial");
        String hospitalName = str(row, "hospitalName");
        JsonNode foldRules = rules.path("specialRules").path("foldRules");
        if (!foldRules.isArray()) {
            return null;
        }
        for (JsonNode rule : foldRules) {
            BillingConditionEvaluator.RowContext ctx = new BillingConditionEvaluator.RowContext(
                    str(row, "type"),
                    str(row, "packName"),
                    str(row, "packageMaterial"),
                    hospitalName,
                    str(row, "department"),
                    doubleOrNull(row, "unitPrice"),
                    0,
                    effectiveCount,
                    null,
                    null,
                    combined
            );
            if (!BillingConditionEvaluator.matchesKeywordsExactToken(str(row, "packName"), rule.path("keywords"))) {
                continue;
            }
            if (BillingConditionEvaluator.matchesRule(rule, ctx)) {
                return rule;
            }
        }
        return null;
    }

    private static int intVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
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
