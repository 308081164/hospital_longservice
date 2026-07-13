package com.hospital.backend.service;

/**
 * 校对任务版本链分组键：同一医院 + 同一源文件构成独立版本序列。
 * 不同月份/不同上传文件不应共享版本号或 UI 卡片。
 */
public final class ReconciliationVersionGroup {

    private static final String UNNAMED_HOSPITAL = "(未命名)";
    private static final String UNNAMED_FILE = "(未命名)";
    private static final String SEPARATOR = "::";

    private ReconciliationVersionGroup() {
    }

    public static String normalizeHospitalName(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return UNNAMED_HOSPITAL;
        }
        return hospitalName.trim();
    }

    public static String normalizeSourceFileName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return UNNAMED_FILE;
        }
        String trimmed = sourceFileName.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    public static String buildKey(String hospitalName, String sourceFileName) {
        return normalizeHospitalName(hospitalName) + SEPARATOR + normalizeSourceFileName(sourceFileName);
    }
}
