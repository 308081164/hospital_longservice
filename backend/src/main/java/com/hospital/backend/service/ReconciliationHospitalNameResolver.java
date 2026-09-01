package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 对账导入时解析医院全称，避免将 Excel 工作表名（科室）误写入 job.hospitalName。
 */
@Service
@RequiredArgsConstructor
public class ReconciliationHospitalNameResolver {

    private static final Pattern DEPARTMENT_NAME = Pattern.compile(
            "^(手术室|门诊部|门诊$|供应室|消毒供应|内镜中心|产房|病区|病房|ICU|供应中心|消毒中心"
                    + "|美容科|骨科|内科|外科|妇科|产科|儿科|眼科|耳鼻喉|口腔科|康复科|急诊科|麻醉科"
                    + "|输血科|病理科|检验科|放射科|超声科|药剂科|营养科|中医科|皮肤科|精神科|肿瘤科"
                    + "|透析室|导管室|介入室|胃镜室|换药室|处置室|治疗室|护士站)([（(].*[）)])?$");

    private static final Pattern HOSPITAL_SUFFIX = Pattern.compile(
            "(医院|诊所|集团|中心|卫生院|卫生服务中心|医疗美容|妇产医院|肛肠医院)$");

    private static final Pattern FILE_BILL_SUFFIX = Pattern.compile("(账单|结款函|汇总|发货单|明细|对账).*$");
    private static final Pattern FILE_MONTH_SUFFIX = Pattern.compile("\\d{1,2}月.*$");
    private static final Pattern FILE_YEAR_PREFIX = Pattern.compile("^\\d{4}[\\s_-]?");

    private final CustomerResolver customerResolver;

    public String resolve(String hospitalNameParam, String sourceFileName) {
        return resolve(hospitalNameParam, sourceFileName, List.of());
    }

    public String resolve(String hospitalNameParam, String sourceFileName, List<String> sheetHospitalNames) {
        for (String candidate : buildCandidates(hospitalNameParam, sourceFileName, sheetHospitalNames)) {
            if (isLikelyDepartmentName(candidate)) {
                continue;
            }
            Optional<Customer> customer = customerResolver.resolveByName(candidate);
            if (customer.isPresent()) {
                return customer.get().getCanonicalName();
            }
        }

        for (String candidate : buildCandidates(hospitalNameParam, sourceFileName, sheetHospitalNames)) {
            if (!isLikelyDepartmentName(candidate)) {
                return candidate;
            }
        }

        return "未命名医院";
    }

    public boolean isLikelyHospitalName(String name) {
        if (name == null || name.isBlank() || isLikelyDepartmentName(name)) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.contains("发货单汇总表")) {
            return false;
        }
        return HOSPITAL_SUFFIX.matcher(trimmed).find() || trimmed.length() >= 6;
    }

    public boolean isLikelyDepartmentName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        if (DEPARTMENT_NAME.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.length() <= 8
                && trimmed.endsWith("科")
                && !HOSPITAL_SUFFIX.matcher(trimmed).find();
    }

    public String inferFromFileName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return "";
        }
        String base = sourceFileName.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = FILE_YEAR_PREFIX.matcher(base).replaceFirst("");
        base = FILE_MONTH_SUFFIX.matcher(base).replaceFirst("");
        base = FILE_BILL_SUFFIX.matcher(base).replaceFirst("");
        return base.trim();
    }

    private List<String> buildCandidates(
            String hospitalNameParam, String sourceFileName, List<String> sheetHospitalNames) {
        Set<String> ordered = new LinkedHashSet<>();
        if (sheetHospitalNames != null) {
            for (String name : sheetHospitalNames) {
                if (isLikelyHospitalName(name)) {
                    addCandidate(ordered, name);
                }
            }
        }
        if (!isLikelyDepartmentName(hospitalNameParam)) {
            addCandidate(ordered, hospitalNameParam);
        }
        addCandidate(ordered, inferFromFileName(sourceFileName));
        if (sourceFileName != null && !sourceFileName.isBlank()) {
            String base = sourceFileName.trim();
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
            int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
            if (slash >= 0) {
                base = base.substring(slash + 1);
            }
            addCandidate(ordered, FILE_YEAR_PREFIX.matcher(base).replaceFirst("").trim());
        }
        addCandidate(ordered, hospitalNameParam);
        return new ArrayList<>(ordered);
    }

    private static void addCandidate(Set<String> ordered, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            ordered.add(trimmed);
        }
    }
}
