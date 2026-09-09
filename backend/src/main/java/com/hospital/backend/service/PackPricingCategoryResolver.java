package com.hospital.backend.service;

import com.hospital.backend.service.PackTypeRegistry.MaterialFamily;
import com.hospital.backend.service.PackTypeRegistry.PackTypeDefinition;

import java.util.Optional;

/**
 * 由 {@link PackTypeRegistry} 权威包类型表推导标准计价通道。
 * 包类型锁定灭菌方式与允许包材；不再从包材/包名反推包类型。
 */
public final class PackPricingCategoryResolver {

    private PackPricingCategoryResolver() {}

    public record Resolution(PackPricingCategory category, String note) {}

    public static Resolution resolve(
            String type,
            String packName,
            String packageMaterial,
            int instrumentCount,
            int packCount) {
        Optional<PackTypeDefinition> def = PackTypeRegistry.match(type);
        if (def.isEmpty()) {
            return new Resolution(PackPricingCategory.UNKNOWN, "包类型不在对照表 16 种之内");
        }
        MaterialFamily materialFamily = PackTypeRegistry.classifyMaterial(packageMaterial);
        if (!PackTypeRegistry.materialAllowed(def.get(), packageMaterial)) {
            if (materialFamily == MaterialFamily.UNKNOWN || materialFamily == MaterialFamily.NONE) {
                return new Resolution(PackPricingCategory.UNKNOWN,
                        "包材「" + (packageMaterial == null ? "" : packageMaterial)
                                + "」与包类型「" + def.get().canonical()
                                + "」允许包材（" + PackTypeRegistry.allowedMaterialsText(def.get()) + "）不符");
            }
            PackPricingCategory category = pricingCategoryOnMaterialMismatch(
                    def.get(), materialFamily, packName);
            String note = "包材「" + (packageMaterial == null ? "" : packageMaterial)
                    + "」与包类型「" + def.get().canonical()
                    + "」允许包材（" + PackTypeRegistry.allowedMaterialsText(def.get()) + "）不符，"
                    + "按包装材料列「" + PackTypeRegistry.describeMaterialFamily(materialFamily) + "」计价";
            String extraNote = buildNote(def.get(), materialFamily, packName);
            if (extraNote != null) {
                note = note + "；" + extraNote;
            }
            return new Resolution(category, note);
        }
        PackPricingCategory category = PackTypeRegistry.pricingCategory(def.get(), materialFamily, packName);
        String note = buildNote(def.get(), materialFamily, packName);
        return new Resolution(category, note);
    }

    /**
     * 类型与包装材料列不一致时，以包装材料列识别出的包材族推导计价通道（仍保留字段核对告警）。
     */
    private static PackPricingCategory pricingCategoryOnMaterialMismatch(
            PackTypeDefinition def, MaterialFamily materialFamily, String packName) {
        String canonical = def.canonical();
        if (canonical.contains("敷料包")) {
            if (materialFamily == MaterialFamily.HIGH_TEMP_PAPER
                    || materialFamily == MaterialFamily.LOW_TEMP_PAPER) {
                return PackPricingCategory.DRESSING_PAPER;
            }
            if (materialFamily == MaterialFamily.NON_WOVEN) {
                return PackPricingCategory.DRESSING_NONWOVEN;
            }
        }
        return PackTypeRegistry.pricingCategory(def, materialFamily, packName);
    }

    private static String buildNote(PackTypeDefinition def, MaterialFamily materialFamily, String packName) {
        if (packName != null && packName.contains("驱血带")
                && materialFamily == MaterialFamily.HIGH_TEMP_PAPER) {
            return "包材为纸塑袋，驱血带按纸塑额外包计价，不按无纺布敷料 W 码分档。";
        }
        return null;
    }

    public static boolean isDressingCategory(PackPricingCategory category) {
        return category == PackPricingCategory.DRESSING_PAPER
                || category == PackPricingCategory.DRESSING_NONWOVEN;
    }
}
