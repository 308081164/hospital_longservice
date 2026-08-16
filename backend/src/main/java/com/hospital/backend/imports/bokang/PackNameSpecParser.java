package com.hospital.backend.imports.bokang;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 包名规格解析器，逻辑对齐 scripts/analyze_pack_name.py。
 */
public final class PackNameSpecParser {

    private static final Pattern ORDER_NO = Pattern.compile("/([ZzWw]\\d+)");
    private static final Pattern PIECE_COUNT = Pattern.compile("[-－](\\d+)件?");
    private static final Pattern BAG_SIZE_MM = Pattern.compile("(\\d+)\\s*[*×xX]\\s*(\\d+)");

    private PackNameSpecParser() {}

    public static String specFingerprint(String packName, String type, String packageMaterial) {
        String normalized = String.format("%s|%s|%s",
                normalize(packName),
                normalize(type),
                normalizeMaterial(packageMaterial));
        return "fp-" + sha256Hex(normalized).substring(0, 16);
    }

    public static String familyCode(String familyName) {
        return "BK-FAM-" + sha256Hex(normalize(familyName)).substring(0, 8);
    }

    public static String variantSku(String fingerprint) {
        return "BK-VAR-" + fingerprint.replace("fp-", "");
    }

    public static ParsedPack parse(String packName, String type, String packageMaterial) {
        ParsedPack parsed = new ParsedPack();
        parsed.packName = packName == null ? "" : packName.trim();
        parsed.type = type == null ? "" : type.trim();
        parsed.packageMaterial = packageMaterial == null ? "" : packageMaterial.trim();
        parsed.familyName = extractFamilyName(parsed.packName);
        parsed.specSuffix = extractSpecSuffix(parsed.packName, parsed.familyName);
        parsed.instrumentCountHint = extractPieceCount(parsed.packName);
        parsed.orderNoPattern = extractOrderNo(parsed.packName);
        parsed.bagInfo = parseBagMaterial(parsed.packageMaterial);
        parsed.displayName = buildDisplayName(parsed);
        parsed.specFingerprint = specFingerprint(parsed.packName, parsed.type, parsed.packageMaterial);
        return parsed;
    }

    private static String extractFamilyName(String packName) {
        if (packName == null || packName.isBlank()) {
            return "";
        }
        String stem = packName;
        int dash = stem.indexOf('-');
        if (dash > 0) {
            stem = stem.substring(0, dash);
        } else {
            int slash = stem.indexOf('/');
            if (slash > 0) {
                stem = stem.substring(0, slash);
            }
        }
        return stem.trim();
    }

    private static String extractSpecSuffix(String packName, String familyName) {
        if (packName == null || familyName == null || familyName.isBlank()) {
            return packName;
        }
        if (packName.startsWith(familyName) && packName.length() > familyName.length()) {
            return packName.substring(familyName.length()).replaceFirst("^[-－]", "").trim();
        }
        return packName;
    }

    /** 从包名提取 `-N` / `-N件` 器械件数 hint。 */
    public static Integer extractPieceCount(String packName) {
        if (packName == null) {
            return null;
        }
        Matcher m = PIECE_COUNT.matcher(packName);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** 从任意文本提取首个 mm 尺寸（如 75*200）。 */
    public static java.util.Optional<MmSize> extractMmSize(String text) {
        if (text == null || text.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = text.replace("×", "*").replace("x", "*").replace("X", "*");
        Matcher mm = BAG_SIZE_MM.matcher(normalized);
        if (!mm.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MmSize(
                Integer.parseInt(mm.group(1)),
                Integer.parseInt(mm.group(2))));
    }

    /** 从类型字段推断包材类别（纸塑袋/无纺布/棉/敷料）。 */
    public static String inferMaterialClassFromType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (type.contains("纸塑袋") || type.contains("纸塑")) {
            return "PAPER_PLASTIC";
        }
        if (type.contains("无纺布")) {
            return "NON_WOVEN";
        }
        if (type.contains("敷料")) {
            return "COTTON_DRESSING";
        }
        if (type.contains("棉")) {
            return "COTTON_DRESSING";
        }
        return null;
    }

    /** 从包装材料字段推断包材类别。 */
    public static String inferMaterialClassFromMaterial(String packageMaterial) {
        BagInfo info = parseBagMaterial(packageMaterial);
        return info.materialClass;
    }

    public static String formatMmSize(MmSize size) {
        return size.widthMm() + "*" + size.heightMm();
    }

    public record MmSize(int widthMm, int heightMm) {}

    private static String extractOrderNo(String packName) {
        if (packName == null) {
            return null;
        }
        Matcher m = ORDER_NO.matcher(packName);
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        return null;
    }

    private static BagInfo parseBagMaterial(String material) {
        BagInfo info = new BagInfo();
        if (material == null || material.isBlank()) {
            return info;
        }
        String normalized = material.replace("×", "*").replace("x", "*").replace("X", "*");
        if (normalized.contains("低温")) {
            info.tempClass = "LT";
        } else if (normalized.contains("高温")) {
            info.tempClass = "HT";
        }
        if (normalized.contains("纸塑")) {
            info.materialClass = "PAPER_PLASTIC";
        } else if (normalized.contains("无纺布")) {
            info.materialClass = "NON_WOVEN";
        } else if (normalized.contains("棉")) {
            info.materialClass = "COTTON_DRESSING";
        }

        Matcher mm = BAG_SIZE_MM.matcher(normalized);
        if (mm.find()) {
            int w = Integer.parseInt(mm.group(1));
            int h = Integer.parseInt(mm.group(2));
            if (w >= 50) {
                info.widthMm = w;
                info.heightMm = h;
                info.sizeLabel = (w / 10) + "cm";
            } else {
                info.sizeLabel = w + "cm";
            }
        } else {
            Matcher cm = Pattern.compile("(\\d+)\\s*cm").matcher(normalized);
            if (cm.find()) {
                info.sizeLabel = cm.group(1) + "cm";
            }
        }
        return info;
    }

    private static String buildDisplayName(ParsedPack parsed) {
        String base = parsed.packName;
        if (parsed.packageMaterial != null && !parsed.packageMaterial.isBlank()) {
            return base + " · " + parsed.packageMaterial;
        }
        return base;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizeMaterial(String material) {
        if (material == null) {
            return "";
        }
        return material.trim().replace("×", "*").replace("x", "*").replace("X", "*").toLowerCase();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static class ParsedPack {
        public String packName;
        public String type;
        public String packageMaterial;
        public String familyName;
        public String specSuffix;
        public Integer instrumentCountHint;
        public String orderNoPattern;
        public BagInfo bagInfo = new BagInfo();
        public String displayName;
        public String specFingerprint;
    }

    public static class BagInfo {
        public String materialClass;
        public String tempClass;
        public Integer widthMm;
        public Integer heightMm;
        public String sizeLabel;
    }
}
