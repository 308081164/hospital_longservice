package com.hospital.backend.service;

import com.hospital.backend.imports.bokang.PackNameSpecParser;
import com.hospital.backend.imports.bokang.PackNameSpecParser.MmSize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 账单行字段一致性校验：包名 vs 类型/包装材料（红），包名件数 vs 器械数列（绿）。
 */
public final class BillRowFieldConsistencyValidator {

    public static final String CODE_BAG_SIZE_MISMATCH = "BAG_SIZE_MISMATCH";
    public static final String CODE_MATERIAL_CLASS_MISMATCH = "MATERIAL_CLASS_MISMATCH";
    public static final String CODE_INSTRUMENT_COUNT_MISMATCH = "INSTRUMENT_COUNT_MISMATCH";

    private BillRowFieldConsistencyValidator() {}

    public record Violation(String code, String message, Map<String, Object> fields) {}

    public static List<Violation> validate(
            String type,
            String packName,
            String packageMaterial,
            int instrumentCount) {
        List<Violation> violations = new ArrayList<>();

        Optional<MmSize> packNameSize = PackNameSpecParser.extractMmSize(packName);
        Optional<MmSize> materialSize = PackNameSpecParser.extractMmSize(packageMaterial);
        if (packNameSize.isPresent() && materialSize.isPresent()) {
            MmSize fromName = packNameSize.get();
            MmSize fromMaterial = materialSize.get();
            if (fromName.widthMm() != fromMaterial.widthMm()
                    || fromName.heightMm() != fromMaterial.heightMm()) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("packNameSize", PackNameSpecParser.formatMmSize(fromName));
                fields.put("materialSize", PackNameSpecParser.formatMmSize(fromMaterial));
                violations.add(new Violation(
                        CODE_BAG_SIZE_MISMATCH,
                        "包名尺寸 " + fields.get("packNameSize")
                                + " 与包装材料尺寸 " + fields.get("materialSize") + " 不一致",
                        fields));
            }
        }

        String typeMaterialClass = PackNameSpecParser.inferMaterialClassFromType(type);
        String materialClass = PackNameSpecParser.inferMaterialClassFromMaterial(packageMaterial);
        if (typeMaterialClass != null && materialClass != null
                && !typeMaterialClass.equals(materialClass)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("typeMaterialClass", typeMaterialClass);
            fields.put("materialClass", materialClass);
            fields.put("type", type);
            fields.put("packageMaterial", packageMaterial);
            violations.add(new Violation(
                    CODE_MATERIAL_CLASS_MISMATCH,
                    "包类型与包装材料类别不一致（类型：" + describeMaterialClass(typeMaterialClass)
                            + "，包装材料：" + describeMaterialClass(materialClass) + "）",
                    fields));
        }

        Integer namePieceCount = PackNameSpecParser.extractPieceCount(packName);
        if (namePieceCount != null && namePieceCount != instrumentCount) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("packNameCount", namePieceCount);
            fields.put("instrumentCount", instrumentCount);
            violations.add(new Violation(
                    CODE_INSTRUMENT_COUNT_MISMATCH,
                    "包名件数 " + namePieceCount + " 与器械数列 " + instrumentCount + " 不一致",
                    fields));
        }

        return violations;
    }

    public static Map<String, Object> toBillingNotes(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Violation violation : violations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", violation.code());
            item.put("message", violation.message());
            if (violation.fields() != null && !violation.fields().isEmpty()) {
                item.putAll(violation.fields());
            }
            items.add(item);
        }
        Map<String, Object> billingNotes = new LinkedHashMap<>();
        billingNotes.put("type", "field_consistency");
        billingNotes.put("violations", items);
        return billingNotes;
    }

    private static String describeMaterialClass(String materialClass) {
        return switch (materialClass) {
            case "PAPER_PLASTIC" -> "纸塑袋";
            case "NON_WOVEN" -> "无纺布";
            case "COTTON_DRESSING" -> "敷料/棉";
            default -> materialClass;
        };
    }
}
