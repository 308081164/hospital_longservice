package com.hospital.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hospital.backend.common.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从 billing_notes JSON 中提取字段一致性 violations（与前端 reconciliationBillingNotes.ts 对齐）。
 */
public final class BillRowBillingNotesSupport {

    private BillRowBillingNotesSupport() {}

    public static List<Map<String, Object>> extractFieldConsistencyViolations(String billingNotesJson) {
        Map<String, Object> billingNotes = parseBillingNotes(billingNotesJson);
        if (billingNotes == null || billingNotes.isEmpty()) {
            return Collections.emptyList();
        }

        Object nested = firstNonNull(
                billingNotes.get("fieldConsistency"),
                billingNotes.get("field_consistency"),
                "field_consistency".equals(String.valueOf(billingNotes.get("type"))) ? billingNotes : null);

        Object rawViolations = firstNonNull(
                billingNotes.get("consistencyViolations"),
                billingNotes.get("consistency_violations"),
                nested instanceof Map<?, ?> nestedMap ? nestedMap.get("violations") : null);

        if (!(rawViolations instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> violations = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                violations.add(typed);
            }
        }
        return violations;
    }

    public static List<Map<String, Object>> extractBillingValidationViolations(String billingNotesJson) {
        Map<String, Object> billingNotes = parseBillingNotes(billingNotesJson);
        if (billingNotes == null || billingNotes.isEmpty()) {
            return Collections.emptyList();
        }

        Object nested = firstNonNull(
                billingNotes.get("billingValidation"),
                billingNotes.get("billing_validation"),
                "billing_validation".equals(String.valueOf(billingNotes.get("type"))) ? billingNotes : null);

        Object rawViolations = firstNonNull(
                billingNotes.get("validationViolations"),
                billingNotes.get("validation_violations"),
                nested instanceof Map<?, ?> nestedMap ? nestedMap.get("violations") : null);

        if (!(rawViolations instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> violations = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                violations.add(typed);
            }
        }
        return violations;
    }

    public static boolean hasFieldConsistencyViolations(String billingNotesJson) {
        return !extractFieldConsistencyViolations(billingNotesJson).isEmpty();
    }

    /** 字段核对（一致性 amber + 校验 error）是否存在任一异常。 */
    public static boolean hasAnyFieldCheckViolations(String billingNotesJson) {
        return hasFieldConsistencyViolations(billingNotesJson)
                || !extractBillingValidationViolations(billingNotesJson).isEmpty();
    }

    public static String summarizeFieldConsistencyViolations(String billingNotesJson) {
        return extractFieldConsistencyViolations(billingNotesJson).stream()
                .map(BillRowBillingNotesSupport::violationMessage)
                .filter(Objects::nonNull)
                .filter(message -> !message.isBlank())
                .collect(Collectors.joining("；"));
    }

    /** 汇总全部字段核对异常（一致性 + 校验），用于异常导出「字段核对问题」列。 */
    public static String summarizeAllFieldCheckViolations(String billingNotesJson) {
        return java.util.stream.Stream.concat(
                        extractFieldConsistencyViolations(billingNotesJson).stream(),
                        extractBillingValidationViolations(billingNotesJson).stream())
                .map(BillRowBillingNotesSupport::violationMessage)
                .filter(Objects::nonNull)
                .filter(message -> !message.isBlank())
                .collect(Collectors.joining("；"));
    }

    private static String violationMessage(Map<String, Object> violation) {
        Object message = violation.get("message");
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }
        Object code = violation.get("code");
        return code != null ? String.valueOf(code) : null;
    }

    private static Map<String, Object> parseBillingNotes(String billingNotesJson) {
        if (billingNotesJson == null || billingNotesJson.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.getObjectMapper().readValue(
                    billingNotesJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
