package com.hospital.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单行字段校验（error 级）：敷料包类型豁免；其余包类型在包装材料为空或器械数为 0 时记异常。
 * 产出 billing_validation 结构（severity=error），前端按「字段核对错误」红色高亮，
 * 与 BillRowFieldConsistencyValidator 的 amber 一致性核对互补。
 */
public final class BillRowBillingValidator {

    public static final String CODE_BLANK_PACKAGE_MATERIAL = "BLANK_PACKAGE_MATERIAL";
    public static final String CODE_ZERO_INSTRUMENT_COUNT = "ZERO_INSTRUMENT_COUNT";
    public static final String SEVERITY_ERROR = "error";

    private BillRowBillingValidator() {}

    public record Violation(String code, String message, String severity, Map<String, Object> fields) {}

    public static List<Violation> validate(String type, String packageMaterial, int instrumentCount) {
        List<Violation> violations = new ArrayList<>();
        if (isDressingPackType(type)) {
            return violations;
        }
        if (packageMaterial == null || packageMaterial.isBlank()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("type", type == null ? "" : type);
            fields.put("packageMaterial", "");
            violations.add(new Violation(
                    CODE_BLANK_PACKAGE_MATERIAL,
                    "包装材料为空",
                    SEVERITY_ERROR,
                    fields));
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

    /** 敷料包类型豁免：包装材料/器械数列允许留空或为 0。 */
    private static boolean isDressingPackType(String type) {
        return type != null && type.contains("敷料包");
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
