package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationJob;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Function;

/**
 * Resolves D8 (row 8) display text for legacy bill export templates.
 */
@Component
public class D8DisplayNameResolver {

    private static final Set<String> DEFAULT_RULE_NAMES = Set.of(
            "标准灭菌计费规则",
            "标准灭菌计费规则 v2.0",
            "Standard Sterilization Billing Rules");

    public String resolve(HospitalReconciliationJob job, String d8DisplaySource) {
        return resolve(job, d8DisplaySource, null);
    }

    public String resolve(
            HospitalReconciliationJob job,
            String d8DisplaySource,
            Function<String, String> originalFileD8Reader) {
        if (job == null) {
            return "";
        }
        String source = d8DisplaySource != null && !d8DisplaySource.isBlank()
                ? d8DisplaySource
                : BillExportLayoutResolver.D8_AUTO;
        return switch (source) {
            case BillExportLayoutResolver.D8_HOSPITAL_NAME -> firstNonBlank(job.getHospitalName());
            case BillExportLayoutResolver.D8_RULE_NAME -> resolveRuleName(job, originalFileD8Reader);
            default -> resolveAuto(job, originalFileD8Reader);
        };
    }

    private String resolveAuto(HospitalReconciliationJob job, Function<String, String> originalFileD8Reader) {
        String hospital = firstNonBlank(job.getHospitalName());
        if (hospital != null) {
            return hospital;
        }
        String plan = firstNonBlank(job.getPlanName());
        if (plan != null) {
            return plan;
        }
        String rule = firstNonBlank(job.getRuleName());
        if (rule != null && !isDefaultRuleName(rule)) {
            return rule;
        }
        if (originalFileD8Reader != null && job.getSourceFilePath() != null) {
            String original = firstNonBlank(originalFileD8Reader.apply(job.getSourceFilePath()));
            if (original != null) {
                return original;
            }
        }
        return hospital != null ? hospital : "";
    }

    private String resolveRuleName(HospitalReconciliationJob job, Function<String, String> originalFileD8Reader) {
        String plan = firstNonBlank(job.getPlanName());
        if (plan != null) {
            return plan;
        }
        String rule = firstNonBlank(job.getRuleName());
        if (rule != null) {
            return rule;
        }
        if (originalFileD8Reader != null && job.getSourceFilePath() != null) {
            String original = firstNonBlank(originalFileD8Reader.apply(job.getSourceFilePath()));
            if (original != null) {
                return original;
            }
        }
        return firstNonBlank(job.getHospitalName(), "");
    }

    public boolean isDefaultRuleName(String ruleName) {
        if (ruleName == null || ruleName.isBlank()) {
            return false;
        }
        String trimmed = ruleName.trim();
        return DEFAULT_RULE_NAMES.contains(trimmed)
                || trimmed.startsWith("标准灭菌计费规则");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
