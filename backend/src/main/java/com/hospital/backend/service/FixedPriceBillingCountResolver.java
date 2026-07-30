package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一特殊固定价规则的计费件数与单价计算，供 {@link PricingEngine} 与 {@link com.hospital.backend.export.ExportFixedPriceApplier} 共用。
 */
public final class FixedPriceBillingCountResolver {

    public static final String PIECE_COUNT_SOURCE_EFFECTIVE = "EFFECTIVE_COUNT";
    public static final String PIECE_COUNT_SOURCE_ZSD_PER_PACK = "ZSD_PER_PACK";
    public static final String PIECE_COUNT_SOURCE_PACK_NAME_LAST_NUMBER = "PACK_NAME_LAST_NUMBER";

    private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+)(?!.*\\d)");

    private FixedPriceBillingCountResolver() {
    }

    public record FixedPriceComputation(
            BillingMode billingMode,
            double basePrice,
            int pieceCount,
            double unitPrice,
            double totalPrice,
            String noteSuffix) {
    }

    /**
     * 从 compiled rule 解析 billingMode；缺失时按 pricePerInstrument 与 keywords 推断（向后兼容）。
     */
    public static BillingMode resolveBillingMode(JsonNode rule) {
        BillingMode explicit = BillingMode.fromString(rule.path("billingMode").asText(null));
        if (explicit != null) {
            return explicit;
        }
        if (rule.path("pricePerInstrument").asBoolean(false)) {
            if (hasKeyword(rule, "刮勺探针")) {
                return BillingMode.PACK_NAME_SUFFIX;
            }
            return BillingMode.PER_INSTRUMENT;
        }
        return BillingMode.PER_PACK;
    }

    public static FixedPriceComputation compute(JsonNode rule, RowInput row, int effectiveCount) {
        double basePrice = rule.path("price").asDouble(Double.NaN);
        if (Double.isNaN(basePrice)) {
            return null;
        }
        BillingMode mode = resolveBillingMode(rule);
        int packCount = Math.max(1, row.packCount());
        int pieceCount = resolvePieceCount(rule, mode, row, effectiveCount);
        double unitPrice;
        double totalPrice;
        String noteSuffix;
        switch (mode) {
            case PER_PACK -> {
                unitPrice = round(basePrice);
                totalPrice = round(unitPrice * packCount);
                noteSuffix = "，单价按 " + fmt(unitPrice) + " 元。";
            }
            case PER_INSTRUMENT, PACK_NAME_SUFFIX -> {
                pieceCount = Math.max(1, pieceCount);
                unitPrice = round(basePrice * pieceCount);
                totalPrice = round(unitPrice * packCount);
                noteSuffix = "，按每件 " + fmt(basePrice) + " 元，单包计费件数 "
                        + pieceCount + " 件，单价按 " + fmt(unitPrice) + " 元。";
            }
            default -> throw new IllegalStateException("Unexpected billing mode: " + mode);
        }
        return new FixedPriceComputation(mode, basePrice, pieceCount, unitPrice, totalPrice, noteSuffix);
    }

    /**
     * 导出层计算：无 effectiveCount 预处理，按行原始字段估算单包件数。
     */
    public static FixedPriceComputation computeForExport(JsonNode rule, ExportRowInput row) {
        double basePrice = rule.path("price").asDouble(Double.NaN);
        if (Double.isNaN(basePrice)) {
            return null;
        }
        int packCount = Math.max(1, row.packCount());
        int instrumentCount = row.instrumentCount() > 0 ? row.instrumentCount() : packCount;
        int effectiveCount = packCount > 1 && !isZsdInstrumentPackType(row.type())
                ? Math.max(1, (int) Math.round((double) instrumentCount / packCount))
                : Math.max(1, instrumentCount);

        RowInput rowInput = new RowInput(row.type(), row.packName(), row.combined(), packCount, instrumentCount);
        return compute(rule, rowInput, effectiveCount);
    }

    public record RowInput(String type, String packName, String combined, int packCount, int instrumentCount) {
    }

    public record ExportRowInput(String type, String packName, String combined, int packCount, int instrumentCount) {
    }

    static int resolvePieceCount(JsonNode rule, BillingMode mode, RowInput row, int effectiveCount) {
        return switch (mode) {
            case PER_PACK -> effectiveCount;
            case PACK_NAME_SUFFIX -> {
                int suffix = extractPackNameSuffix(row.packName());
                yield suffix > 0 ? suffix : effectiveCount;
            }
            case PER_INSTRUMENT -> resolveInstrumentPieceCount(rule, row, effectiveCount);
        };
    }

    static int resolveInstrumentPieceCount(JsonNode rule, RowInput row, int effectiveCount) {
        if (isZsdInstrumentPackType(row.type()) && row.packCount() > 1) {
            return Math.max(1, (int) Math.round((double) row.instrumentCount() / row.packCount()));
        }
        String pieceCountSource = rule.path("pieceCountSource").asText(PIECE_COUNT_SOURCE_EFFECTIVE);
        if (PIECE_COUNT_SOURCE_PACK_NAME_LAST_NUMBER.equals(pieceCountSource)) {
            int suffix = extractPackNameSuffix(row.packName());
            return suffix > 0 ? suffix : effectiveCount;
        }
        return effectiveCount;
    }

    static int extractPackNameSuffix(String packName) {
        if (packName == null || packName.isBlank()) {
            return 0;
        }
        String stem = packName.split("/")[0];
        Matcher m = LAST_NUMBER.matcher(stem);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    static boolean isZsdInstrumentPackType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String normalized = type.replaceAll("\\s+", "");
        return normalized.contains("器械包(ZSD)")
                || (normalized.contains("器械包") && normalized.toUpperCase().contains("ZSD"));
    }

    static boolean hasKeyword(JsonNode rule, String keyword) {
        JsonNode keywords = rule.path("keywords");
        if (!keywords.isArray()) {
            return false;
        }
        for (JsonNode kwNode : keywords) {
            if (keyword.equals(kwNode.asText())) {
                return true;
            }
        }
        return false;
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.2f", value);
    }
}
