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
    /** 器械名（汉字/字母）后紧跟数字、位于斜杠前 stem 末尾，如 排针20 → 20。 */
    private static final Pattern TRAILING_NAME_DIGITS =
            Pattern.compile("([\\p{L}\\p{Script=Han}]+)(\\d+)$");
    /** 无连字符的复合包名：盆1碗1、盆1碗2盘2杯1 → 各段数字累加。 */
    private static final Pattern COMPACT_NAME_DIGIT_SEGMENTS =
            Pattern.compile("([\\p{L}\\p{Script=Han}]+)(\\d+)");
    /** stem 内无连字符时的 {@code N件}，如 宫腔镜包26件、抛光车针盒6件盒1 → 6。 */
    private static final Pattern STANDALONE_PIECE_COUNT =
            Pattern.compile("(\\d+)件");
    /** {@code N件} 且非 {@code -N件} 连字符段，避免与 hyphen 双计。 */
    private static final Pattern STANDALONE_PIECE_NOT_HYPHEN =
            Pattern.compile("(?<![-－])(\\d+)件");
    /** 针架复合：器械数列通常按架计，不做字段件数核对。 */
    private static final Pattern NEEDLE_RACK_PATTERN = Pattern.compile("针架\\d+针\\d+");
    /** 紧凑复合至少两段「名+数」，如 盆1碗1；单段 排针20 不算。 */
    private static final Pattern COMPACT_MULTI_SEGMENT =
            Pattern.compile("[\\p{L}\\p{Script=Han}]\\d+[\\p{L}\\p{Script=Han}]\\d+");
    private static final Pattern GLUED_HYPHEN_ORDER_CODE =
            Pattern.compile("(?i)(-\\d+)([ZzWw]\\d+)$");
    /** 括号后粘连订单码：胸外镜头-1（盒1）W9050。 */
    private static final Pattern GLUED_ORDER_AFTER_PAREN =
            Pattern.compile("(?i)(?<=[）)])([ZzWw]\\d+)$");
    /** stem 末尾粘连订单码（无斜杠）。 */
    private static final Pattern GLUED_ORDER_AFTER_STEM =
            Pattern.compile("(?i)(?<=[\\p{Script=Han}\\d])([ZzWw]\\d+)$");
    /** 容器计数：{@code 件 盒1}、{@code 件盒1}、{@code -6盒1} 中非 Han 字符后的 盒/筐/盘。 */
    private static final Pattern CONTAINER_AFTER_PIECE =
            Pattern.compile("件\\s*(?:盒|筐|盘)(\\d+)");
    private static final Pattern CONTAINER_AFTER_NON_HAN =
            Pattern.compile("(?<![\\p{Script=Han}])(?:盒|筐|盘)(\\d+)");
    private static final Pattern PAREN_BOX_PIECE_COUNT =
            Pattern.compile("带盒([\\d两二三四五六七八九十]+)(?:件)?");
    private static final Pattern PAREN_GROUP = Pattern.compile("[（(]([^）)]*)[）)]");
    private static final Pattern PRODUCT_MODEL_TOKEN =
            Pattern.compile("\\d+[A-Za-z][A-Za-z0-9]*\\d{3,}|[A-Za-z][A-Za-z0-9]*\\d{4,}");
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

    /** 从包名提取首个 `-N` / `-N件` 器械件数 hint（单件规格）。 */
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

    /**
     * 从包名（斜杠订单后缀之前）提取器械件数合计。
     * <p>计数与计价彻底隔离：仅统计 {@code /} 前各数字 token 之和，{@code 盒/筐/盘} 与
     * {@code -N/-N件} 同等参与累加，不在计数阶段区分「包装盒」语义（包装盒加价等留给计价规则）。
     * <p>算法：斜杠前各计数 token 直接累加（连字符段、非连字符 {@code N件}、紧凑名+数、括号内连字符段），
     * 再累加 {@code 盒N/筐N/盘N}；无互斥 early-return 分支。
     */
    public static Integer extractTotalPieceCountFromPackName(String packName) {
        if (packName == null) {
            return null;
        }
        String normalized = normalizePackNameForPieceCount(packName);
        String stem = normalizeStemForPieceCount(packNameStemBeforeSlash(normalized));
        if (stem.isBlank() || shouldSkipPieceCountExtractionStem(stem)) {
            return null;
        }
        BasePieceCount base = extractBasePieceCount(stem);
        if (base == null) {
            return null;
        }
        int baseCount = base.count();
        Integer parenBoxTotal = extractParenBoxPieceTotal(stem);
        if (parenBoxTotal != null && parenBoxTotal > baseCount) {
            baseCount = parenBoxTotal;
        }
        return baseCount + sumExplicitContainerCounts(stem, baseCount, base.compactCompound());
    }

    /**
     * 仅斜杠码、无件数标注的包名：默认单包 1 件（如 持针器/z1029）。
     */
    public static boolean isImplicitSinglePiecePerPack(String packName) {
        if (packName == null || packName.isBlank() || shouldSkipPieceCountExtraction(packName)) {
            return false;
        }
        if (extractTotalPieceCountFromPackName(packName) != null) {
            return false;
        }
        String normalized = normalizePackNameForPieceCount(packName);
        String stem = normalizeStemForPieceCount(packNameStemBeforeSlash(normalized));
        if (stem.contains("（") || stem.contains("(")) {
            return false;
        }
        if (PIECE_COUNT.matcher(stem).find() || STANDALONE_PIECE_COUNT.matcher(stem).find()) {
            return false;
        }
        if (COMPACT_MULTI_SEGMENT.matcher(stem).find() || stem.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        return ORDER_NO.matcher(normalized).find();
    }

    /**
     * 是否跳过「包名件数 × 包数 vs 器械数列」核对（针架、手机/型号编码等）。
     */
    public static boolean shouldSkipPieceCountExtraction(String packName) {
        if (packName == null || packName.isBlank()) {
            return false;
        }
        String normalized = normalizePackNameForPieceCount(packName);
        return shouldSkipPieceCountExtractionStem(
                normalizeStemForPieceCount(packNameStemBeforeSlash(normalized)));
    }

    private static boolean shouldSkipPieceCountExtractionStem(String stem) {
        if (stem == null || stem.isBlank()) {
            return false;
        }
        return NEEDLE_RACK_PATTERN.matcher(stem).find()
                || isProductModelPackName(stem);
    }

    private record BasePieceCount(int count, boolean compactCompound) {}

    private static String normalizePackNameForPieceCount(String packName) {
        String s = packName.trim();
        Matcher gluedHyphen = GLUED_HYPHEN_ORDER_CODE.matcher(s);
        if (gluedHyphen.find()) {
            s = gluedHyphen.replaceFirst("$1/$2");
        }
        Matcher afterParen = GLUED_ORDER_AFTER_PAREN.matcher(s);
        if (afterParen.find()) {
            s = afterParen.replaceFirst("");
        }
        Matcher afterStem = GLUED_ORDER_AFTER_STEM.matcher(s);
        if (afterStem.find()) {
            s = afterStem.replaceFirst("");
        }
        return s.trim();
    }

    private static String normalizeStemForPieceCount(String stem) {
        return stem.replace('，', ' ').replace(',', ' ').trim();
    }

    private static BasePieceCount extractBasePieceCount(String stem) {
        if (isProductModelPackName(stem)) {
            return null;
        }
        String countStem = PAREN_GROUP.matcher(stem).replaceAll("");
        java.util.List<int[]> hyphenSpans = new java.util.ArrayList<>();
        Matcher hyphenMatcher = PIECE_COUNT.matcher(countStem);
        int hyphenSum = 0;
        boolean foundHyphen = false;
        while (hyphenMatcher.find()) {
            hyphenSum += Integer.parseInt(hyphenMatcher.group(1));
            hyphenSpans.add(new int[] {hyphenMatcher.start(), hyphenMatcher.end()});
            foundHyphen = true;
        }
        if (foundHyphen) {
            int compactExtra = 0;
            Matcher compactMatcher = COMPACT_NAME_DIGIT_SEGMENTS.matcher(countStem);
            while (compactMatcher.find()) {
                if (!isValidCompactCountSegment(compactMatcher, countStem)) {
                    continue;
                }
                if (!overlapsAnySpan(compactMatcher.start(), compactMatcher.end(), hyphenSpans)) {
                    compactExtra += Integer.parseInt(compactMatcher.group(2));
                }
            }
            return new BasePieceCount(hyphenSum + compactExtra, false);
        }

        int parenHyphenSum = 0;
        boolean foundParenHyphen = false;
        Matcher parenGroups = PAREN_GROUP.matcher(stem);
        while (parenGroups.find()) {
            Matcher innerHyphen = PIECE_COUNT.matcher(parenGroups.group(1));
            while (innerHyphen.find()) {
                parenHyphenSum += Integer.parseInt(innerHyphen.group(1));
                foundParenHyphen = true;
            }
        }
        if (foundParenHyphen) {
            return new BasePieceCount(parenHyphenSum, false);
        }

        Matcher standalonePieceMatcher = STANDALONE_PIECE_COUNT.matcher(stem);
        if (standalonePieceMatcher.find()) {
            return new BasePieceCount(Integer.parseInt(standalonePieceMatcher.group(1)), false);
        }

        Matcher compactMatcher = COMPACT_NAME_DIGIT_SEGMENTS.matcher(stem);
        int compactSum = 0;
        int compactSegments = 0;
        while (compactMatcher.find()) {
            compactSum += Integer.parseInt(compactMatcher.group(2));
            compactSegments++;
        }
        if (compactSegments >= 2) {
            return new BasePieceCount(compactSum, true);
        }

        Matcher trailingMatcher = TRAILING_NAME_DIGITS.matcher(stem);
        if (trailingMatcher.find()) {
            String namePart = trailingMatcher.group(1);
            int trailingCount = Integer.parseInt(trailingMatcher.group(2));
            if (namePart.endsWith("少") || trailingCount > 99) {
                return null;
            }
            return new BasePieceCount(trailingCount, false);
        }
        return null;
    }

    /** 连字符 stem 上额外累加的「名+数」段：排除角度、型号、订单码与盒/筐/盘容器段。 */
    private static boolean isValidCompactCountSegment(Matcher compactMatcher, String text) {
        String namePart = compactMatcher.group(1);
        if (namePart.endsWith("盒") || namePart.endsWith("筐") || namePart.endsWith("盘")) {
            return false;
        }
        if (namePart.length() <= 1) {
            return false;
        }
        int digitEnd = compactMatcher.end(2);
        if (digitEnd < text.length()) {
            char next = text.charAt(digitEnd);
            if (next == '°' || next == '度' || next == '号') {
                return false;
            }
        }
        if (namePart.length() == 1 && namePart.chars().allMatch(ch -> ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z')) {
            String digits = compactMatcher.group(2);
            if (digits.length() >= 4) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlapsAnySpan(int start, int end, java.util.List<int[]> spans) {
        for (int[] span : spans) {
            if (start < span[1] && end > span[0]) {
                return true;
            }
        }
        return false;
    }

    private static int sumExplicitContainerCounts(String stem, int baseCount, boolean compactCompound) {
        if (compactCompound) {
            // 紧凑复合已将 盒/筐/盘 段计入 base（针7盒1 → 8）
            return 0;
        }
        int sum = 0;
        Matcher parenMatcher = PAREN_GROUP.matcher(stem);
        int lastEnd = 0;
        StringBuilder outside = new StringBuilder();
        while (parenMatcher.find()) {
            outside.append(stem, lastEnd, parenMatcher.start());
            String inner = parenMatcher.group(1);
            if (!PAREN_BOX_PIECE_COUNT.matcher(inner).find()) {
                sum += sumContainerTokensInText(inner);
            }
            lastEnd = parenMatcher.end();
        }
        outside.append(stem.substring(lastEnd));
        sum += sumContainerTokensInText(outside.toString());
        return sum;
    }

    private static Integer extractParenBoxPieceTotal(String stem) {
        Integer maxTotal = null;
        Matcher parenMatcher = PAREN_GROUP.matcher(stem);
        while (parenMatcher.find()) {
            Matcher boxPiece = PAREN_BOX_PIECE_COUNT.matcher(parenMatcher.group(1));
            if (boxPiece.find()) {
                int total = parseChineseOrArabicCount(boxPiece.group(1));
                if (maxTotal == null || total > maxTotal) {
                    maxTotal = total;
                }
            }
        }
        return maxTotal;
    }

    private static int parseChineseOrArabicCount(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (token.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(token);
        }
        return switch (token) {
            case "两", "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    private static int sumContainerTokensInText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int sum = 0;
        Matcher afterPiece = CONTAINER_AFTER_PIECE.matcher(text);
        while (afterPiece.find()) {
            sum += Integer.parseInt(afterPiece.group(1));
        }
        String withoutAfterPiece = CONTAINER_AFTER_PIECE.matcher(text).replaceAll("件");
        Matcher afterNonHan = CONTAINER_AFTER_NON_HAN.matcher(withoutAfterPiece);
        while (afterNonHan.find()) {
            sum += Integer.parseInt(afterNonHan.group(1));
        }
        return sum;
    }

    private static boolean isProductModelPackName(String stem) {
        if (stem.contains("手机")) {
            return true;
        }
        if (stem.regionMatches(true, 0, "Z00", 0, 3)) {
            return true;
        }
        return PRODUCT_MODEL_TOKEN.matcher(stem).find();
    }

    private static String packNameStemBeforeSlash(String packName) {
        int slash = packName.indexOf('/');
        if (slash > 0) {
            return packName.substring(0, slash).trim();
        }
        return packName.trim();
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
