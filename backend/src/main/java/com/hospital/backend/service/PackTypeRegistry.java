package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 包类型与包材对照表（{@code pack-type-material-map.json}）权威注册表。
 * <p>系统仅识别表内 16 种包类型；灭菌方式由包类型锁定，不再从包名/包材推断低温。
 */
public final class PackTypeRegistry {

    private PackTypeRegistry() {}

    public enum SterilizationMode {
        HIGH_TEMP,
        LOW_TEMP_EO
    }

    /** 包材族：忽略尺寸、克重等后缀规格。 */
    public enum MaterialFamily {
        NONE,
        NON_WOVEN,
        HIGH_TEMP_PAPER,
        LOW_TEMP_PAPER,
        UNKNOWN
    }

    public record PackTypeDefinition(
            String canonical,
            Set<MaterialFamily> allowedMaterials,
            SterilizationMode sterilization) {}

    private static final List<PackTypeDefinition> DEFINITIONS = loadDefinitions();

    public static List<PackTypeDefinition> allDefinitions() {
        return List.copyOf(DEFINITIONS);
    }

    public static Optional<PackTypeDefinition> match(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeTypeLabel(type);
        for (PackTypeDefinition def : DEFINITIONS) {
            if (normalized.equals(normalizeTypeLabel(def.canonical()))) {
                return Optional.of(def);
            }
        }
        for (PackTypeDefinition def : DEFINITIONS) {
            for (String alias : aliasesOf(def.canonical())) {
                if (normalized.equals(normalizeTypeLabel(alias))) {
                    return Optional.of(def);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isKnownPackType(String type) {
        return match(type).isPresent();
    }

    public static boolean packTypeEquivalent(String expected, String actual) {
        Optional<PackTypeDefinition> e = match(expected);
        Optional<PackTypeDefinition> a = match(actual);
        if (e.isPresent() && a.isPresent()) {
            return e.get().canonical().equals(a.get().canonical());
        }
        return normalizeTypeLabel(expected).equals(normalizeTypeLabel(actual));
    }

    public static MaterialFamily classifyMaterial(String packageMaterial) {
        if (packageMaterial == null || packageMaterial.isBlank()) {
            return MaterialFamily.NONE;
        }
        String trimmed = packageMaterial.trim();
        if ("无".equals(trimmed)) {
            return MaterialFamily.NONE;
        }
        String normalized = normalizeMaterialText(trimmed);
        boolean hasPaper = normalized.contains("纸塑袋") || normalized.contains("纸塑");
        boolean hasNonWoven = normalized.contains("无纺布");
        boolean hasLowTemp = normalized.contains("低温");
        if (hasPaper && hasLowTemp) {
            return MaterialFamily.LOW_TEMP_PAPER;
        }
        if (normalized.contains("低温灭菌")) {
            return MaterialFamily.LOW_TEMP_PAPER;
        }
        if (hasPaper) {
            return MaterialFamily.HIGH_TEMP_PAPER;
        }
        if (hasNonWoven) {
            return MaterialFamily.NON_WOVEN;
        }
        return MaterialFamily.UNKNOWN;
    }

    public static boolean materialAllowed(PackTypeDefinition def, String packageMaterial) {
        MaterialFamily family = classifyMaterial(packageMaterial);
        if (family == MaterialFamily.UNKNOWN) {
            return false;
        }
        return def.allowedMaterials().contains(family);
    }

    public static boolean isDressingPackType(String type) {
        return match(type).map(def -> def.canonical().contains("敷料包")).orElse(false);
    }

    public static boolean allowsBlankMaterial(PackTypeDefinition def) {
        return def.allowedMaterials().contains(MaterialFamily.NONE);
    }

    public static SterilizationMode sterilizationOf(String type) {
        return match(type).map(PackTypeDefinition::sterilization).orElse(null);
    }

    public static boolean isLowTempPackType(String type) {
        return match(type).map(def -> def.sterilization() == SterilizationMode.LOW_TEMP_EO).orElse(false);
    }

    public static PackPricingCategory pricingCategory(
            PackTypeDefinition def, MaterialFamily materialFamily, String packName) {
        String canonical = def.canonical();
        if (canonical.contains("敷料包")) {
            if (canonical.contains("纸塑袋") || materialFamily == MaterialFamily.HIGH_TEMP_PAPER) {
                return PackPricingCategory.DRESSING_PAPER;
            }
            return PackPricingCategory.DRESSING_NONWOVEN;
        }
        if (materialFamily == MaterialFamily.HIGH_TEMP_PAPER
                || materialFamily == MaterialFamily.LOW_TEMP_PAPER) {
            if (packName != null && packName.contains("驱血带")) {
                return PackPricingCategory.INSTRUMENT_PAPER;
            }
            return PackPricingCategory.INSTRUMENT_PAPER;
        }
        if (materialFamily == MaterialFamily.NON_WOVEN) {
            if (canonical.contains("敷料包")
                    || (packName != null && packName.contains("驱血带"))) {
                return PackPricingCategory.DRESSING_NONWOVEN;
            }
            return PackPricingCategory.INSTRUMENT_NONWOVEN;
        }
        if (allowsBlankMaterial(def)) {
            return PackPricingCategory.DRESSING_NONWOVEN;
        }
        return PackPricingCategory.UNKNOWN;
    }

    public static String describeMaterialFamily(MaterialFamily family) {
        return switch (family) {
            case NONE -> "无包材";
            case NON_WOVEN -> "无纺布";
            case HIGH_TEMP_PAPER -> "高温纸塑袋";
            case LOW_TEMP_PAPER -> "低温纸塑袋";
            case UNKNOWN -> "未识别包材";
        };
    }

    public static String allowedMaterialsText(PackTypeDefinition def) {
        List<String> parts = new ArrayList<>();
        for (MaterialFamily family : def.allowedMaterials()) {
            parts.add(describeMaterialFamily(family));
        }
        return String.join("、", parts);
    }

    private static List<PackTypeDefinition> loadDefinitions() {
        try (InputStream in = PackTypeRegistry.class.getResourceAsStream("/pack-type-material-map.json")) {
            if (in == null) {
                throw new IllegalStateException("缺少 pack-type-material-map.json");
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(in);
            List<PackTypeDefinition> defs = new ArrayList<>();
            for (JsonNode node : root.path("packTypes")) {
                String canonical = node.path("canonical").asText("");
                EnumSet<MaterialFamily> materials = EnumSet.noneOf(MaterialFamily.class);
                for (JsonNode mat : node.path("allowedMaterials")) {
                    materials.add(MaterialFamily.valueOf(mat.asText("UNKNOWN")));
                }
                SterilizationMode mode = SterilizationMode.valueOf(
                        node.path("sterilization").asText("HIGH_TEMP"));
                defs.add(new PackTypeDefinition(canonical, Set.copyOf(materials), mode));
            }
            if (defs.size() != 16) {
                throw new IllegalStateException("包类型对照表必须为 16 种，实际 " + defs.size());
            }
            return defs;
        } catch (Exception e) {
            throw new IllegalStateException("加载 pack-type-material-map.json 失败", e);
        }
    }

    private static Set<String> aliasesOf(String canonical) {
        try (InputStream in = PackTypeRegistry.class.getResourceAsStream("/pack-type-material-map.json")) {
            JsonNode root = JsonUtils.getObjectMapper().readTree(in);
            for (JsonNode node : root.path("packTypes")) {
                if (canonical.equals(node.path("canonical").asText())) {
                    Set<String> aliases = new java.util.LinkedHashSet<>();
                    for (JsonNode alias : node.path("aliases")) {
                        aliases.add(alias.asText(""));
                    }
                    return aliases;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Set.of();
    }

    static String normalizeTypeLabel(String type) {
        if (type == null) {
            return "";
        }
        return type.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    static String normalizeMaterialText(String material) {
        return material.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replace('×', 'x')
                .toLowerCase(Locale.ROOT);
    }
}
