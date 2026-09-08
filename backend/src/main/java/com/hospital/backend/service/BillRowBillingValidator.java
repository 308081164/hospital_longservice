package com.hospital.backend.service;

import com.hospital.backend.service.PackTypeRegistry.MaterialFamily;
import com.hospital.backend.service.PackTypeRegistry.PackTypeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 账单行字段校验（error 级）：单价为 0 时所有包类型均记异常；
 * 包类型须在对照表 16 种之内；包材须与包类型允许列表一致（尺寸/克重后缀忽略）；
 * 敷料包类型豁免器械数为 0；允许「无包材」的敷料包豁免包装材料为空。
 */
public final class BillRowBillingValidator {

    public static final String CODE_BLANK_PACKAGE_MATERIAL = "BLANK_PACKAGE_MATERIAL";
    public static final String CODE_ZERO_INSTRUMENT_COUNT = "ZERO_INSTRUMENT_COUNT";
    public static final String CODE_ZERO_UNIT_PRICE = "ZERO_UNIT_PRICE";
    public static final String CODE_UNKNOWN_PACK_TYPE = "UNKNOWN_PACK_TYPE";
    public static final String CODE_PACK_TYPE_MATERIAL_MISMATCH = "PACK_TYPE_MATERIAL_MISMATCH";
    public static final String SEVERITY_ERROR = "error";

    private BillRowBillingValidator() {}

    public record Violation(String code, String message, String severity, Map<String, Object> fields) {}

    public static List<Violation> validate(String type, String packageMaterial, int instrumentCount) {
        return validate(type, packageMaterial, instrumentCount, null);
    }

    public static List<Violation> validate(String type, String packageMaterial, int instrumentCount,
                                           Double unitPrice) {
        List<Violation> violations = new ArrayList<>();
        if (unitPrice != null && unitPrice == 0) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            fields.put("unitPrice", unitPrice);
            violations.add(new Violation(
                    CODE_ZERO_UNIT_PRICE,
                    "单价为 0，请确认是否漏填或免费项目",
                    SEVERITY_ERROR,
                    fields));
        }

        Optional<PackTypeDefinition> packType = PackTypeRegistry.match(type);
        if (packType.isEmpty()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            violations.add(new Violation(
                    CODE_UNKNOWN_PACK_TYPE,
                    "包类型不在对照表 16 种之内，请按《包类型与包材对照表》填写",
                    SEVERITY_ERROR,
                    fields));
            return violations;
        }

        PackTypeDefinition def = packType.get();
        MaterialFamily materialFamily = PackTypeRegistry.classifyMaterial(packageMaterial);
        if (!PackTypeRegistry.materialAllowed(def, packageMaterial)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            fields.put("packageMaterial", packageMaterial == null ? "" : packageMaterial);
            fields.put("allowedMaterials", PackTypeRegistry.allowedMaterialsText(def));
            String actual = PackTypeRegistry.describeMaterialFamily(materialFamily);
            violations.add(new Violation(
                    CODE_PACK_TYPE_MATERIAL_MISMATCH,
                    "包装材料与包类型不符（类型「" + def.canonical()
                            + "」仅允许：" + PackTypeRegistry.allowedMaterialsText(def)
                            + "，当前识别为：" + actual + "）",
                    SEVERITY_ERROR,
                    fields));
        } else if (materialFamily == MaterialFamily.NONE
                && !PackTypeRegistry.allowsBlankMaterial(def)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            fields.put("packageMaterial", "");
            violations.add(new Violation(
                    CODE_BLANK_PACKAGE_MATERIAL,
                    "包装材料为空",
                    SEVERITY_ERROR,
                    fields));
        }

        if (PackTypeRegistry.isDressingPackType(type)) {
            return violations;
        }
        if (instrumentCount == 0) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            fields.put("instrumentCount", instrumentCount);
            violations.add(new Violation(
                    CODE_ZERO_INSTRUMENT_COUNT,
                    "器械数为0",
                    SEVERITY_ERROR,
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
            item.put("severity", violation.severity());
            if (violation.fields() != null && !violation.fields().isEmpty()) {
                item.putAll(violation.fields());
            }
            items.add(item);
        }
        Map<String, Object> billingNotes = new LinkedHashMap<>();
        billingNotes.put("type", "billing_validation");
        billingNotes.put("violations", items);
        return billingNotes;
    }
}
