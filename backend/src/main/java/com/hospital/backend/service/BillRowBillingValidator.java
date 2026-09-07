package com.hospital.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单行字段校验（error 级）：单价为 0 时所有包类型均记异常（敷料包不豁免，
 * 驱血带/敷料包 0 元导入退化路径正是需要被发现的行）；敷料包类型豁免包装材料/器械数两项；
 * 其余包类型在包装材料为空或器械数为 0 时记异常。
 * 产出 billing_validation 结构（severity=error），前端按「字段核对错误」红色高亮，
 * 与 BillRowFieldConsistencyValidator 的 amber 一致性核对互补。
 */
public final class BillRowBillingValidator {

    public static final String CODE_BLANK_PACKAGE_MATERIAL = "BLANK_PACKAGE_MATERIAL";
    public static final String CODE_ZERO_INSTRUMENT_COUNT = "ZERO_INSTRUMENT_COUNT";
    public static final String CODE_ZERO_UNIT_PRICE = "ZERO_UNIT_PRICE";
    public static final String SEVERITY_ERROR = "error";

    private BillRowBillingValidator() {}

    public record Violation(String code, String message, String severity, Map<String, Object> fields) {}

    /** 不校验单价的兼容入口（单价缺失时跳过 ZERO_UNIT_PRICE 检查）。 */
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
