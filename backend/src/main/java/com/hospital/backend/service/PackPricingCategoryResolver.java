package com.hospital.backend.service;

/**
 * Resolves which standard-pricing channel applies. Package material takes precedence over
 * bill type; pack-name keywords (e.g. tourniquet) never override a paper-plastic material.
 */
public final class PackPricingCategoryResolver {

    private PackPricingCategoryResolver() {}

    public record Resolution(PackPricingCategory category, String note) {}

    private enum MaterialBase {
        PAPER,
        NON_WOVEN,
        UNKNOWN
    }

    public static Resolution resolve(
            String type,
            String packName,
            String packageMaterial,
            int instrumentCount,
            int packCount) {
        String typ = type == null ? "" : type.trim();
        String pack = packName == null ? "" : packName.trim();
        String mat = packageMaterial == null ? "" : packageMaterial.trim();

        MaterialBase base = resolveMaterialBase(typ, mat);
        return switch (base) {
            case PAPER -> resolvePaper(typ, pack, mat, instrumentCount, packCount);
            case NON_WOVEN -> resolveNonWoven(typ, pack);
            case UNKNOWN -> new Resolution(PackPricingCategory.UNKNOWN, null);
        };
    }

    private static MaterialBase resolveMaterialBase(String type, String material) {
        if (material.contains("无纺布") && !material.contains("纸塑袋")) {
            return MaterialBase.NON_WOVEN;
        }
        if (material.contains("纸塑袋") || material.contains("低温灭菌") || material.contains("双层袋")) {
            return MaterialBase.PAPER;
        }
        if (type.contains("无纺布") && !type.contains("纸塑袋")) {
            return MaterialBase.NON_WOVEN;
        }
        if (type.contains("纸塑袋") || type.contains("低温灭菌")) {
            return MaterialBase.PAPER;
        }
        return MaterialBase.UNKNOWN;
    }

    private static Resolution resolvePaper(
            String type,
            String packName,
            String material,
            int instrumentCount,
            int packCount) {
        if (isDressingPaperType(type)
                || isCottonDressingPaperRow(packName, type, instrumentCount, packCount)) {
            return new Resolution(PackPricingCategory.DRESSING_PAPER, null);
        }
        if (packName.contains("驱血带")) {
            return new Resolution(
                    PackPricingCategory.INSTRUMENT_PAPER,
                    "包材为纸塑袋，驱血带按纸塑额外包计价，不按无纺布敷料 W 码分档。");
        }
        return new Resolution(PackPricingCategory.INSTRUMENT_PAPER, null);
    }

    private static Resolution resolveNonWoven(String type, String packName) {
        if (type.contains("敷料包")
                || (packName.contains("驱血带") && isNonWovenDressingPackName(packName, type))) {
            return new Resolution(PackPricingCategory.DRESSING_NONWOVEN, null);
        }
        return new Resolution(PackPricingCategory.INSTRUMENT_NONWOVEN, null);
    }

    private static boolean isDressingPaperType(String type) {
        return type.contains("敷料包") && type.contains("纸塑袋");
    }

    private static boolean isCottonDressingPackName(String packName) {
        return packName.contains("棉球") && !packName.contains("棉球缸");
    }

    private static boolean isCottonDressingPaperRow(
            String packName, String type, int instrumentCount, int packCount) {
        if (!isCottonDressingPackName(packName) || !type.contains("纸塑袋")) {
            return false;
        }
        if (type.contains("敷料包") && type.contains("纸塑袋")) {
            return true;
        }
        int perPack = packCount > 1
                ? (int) Math.round((double) instrumentCount / Math.max(1, packCount))
                : instrumentCount;
        return perPack <= 0;
    }

    private static boolean isNonWovenDressingPackName(String packName, String type) {
        return packName.contains("驱血带") || type.contains("敷料");
    }

    public static boolean isDressingCategory(PackPricingCategory category) {
        return category == PackPricingCategory.DRESSING_PAPER
                || category == PackPricingCategory.DRESSING_NONWOVEN;
    }
}
