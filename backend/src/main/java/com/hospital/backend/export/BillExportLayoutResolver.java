package com.hospital.backend.export;

import com.hospital.backend.export.model.ColumnMappingConfig;
import com.hospital.backend.service.impl.HospitalReconciliationServiceImpl;
import org.springframework.stereotype.Component;

/**
 * Resolves bill export layout (dept split vs combined) from export template column_mapping.
 */
@Component
public class BillExportLayoutResolver {

    public static final String LAYOUT_AUTO = "auto";
    public static final String LAYOUT_DEPT_SPLIT = "dept_split";
    public static final String LAYOUT_COMBINED = "combined";

    public static final String D8_AUTO = "auto";
    public static final String D8_HOSPITAL_NAME = "hospitalName";
    public static final String D8_RULE_NAME = "ruleName";

    public static final String SHEET_MODE_MULTI_DEPT = "multi_dept";
    public static final String SHEET_MODE_SINGLE_COMBINED = "single_combined";

    public String normalizeBillLayout(String raw) {
        if (raw == null || raw.isBlank()) {
            return LAYOUT_AUTO;
        }
        return switch (raw.trim().toLowerCase()) {
            case LAYOUT_DEPT_SPLIT, "dept-split", "deptsplit" -> LAYOUT_DEPT_SPLIT;
            case LAYOUT_COMBINED -> LAYOUT_COMBINED;
            default -> LAYOUT_AUTO;
        };
    }

    public String normalizeD8DisplaySource(String raw) {
        if (raw == null || raw.isBlank()) {
            return D8_AUTO;
        }
        return switch (raw.trim()) {
            case D8_HOSPITAL_NAME, "hospital_name", "hospital" -> D8_HOSPITAL_NAME;
            case D8_RULE_NAME, "rule_name", "rule" -> D8_RULE_NAME;
            default -> D8_AUTO;
        };
    }

    public String resolveBillLayout(ColumnMappingConfig config) {
        if (config == null || config.getBillLayout() == null) {
            return LAYOUT_AUTO;
        }
        return normalizeBillLayout(config.getBillLayout());
    }

    public String resolveD8DisplaySource(ColumnMappingConfig config) {
        if (config == null || config.getD8DisplaySource() == null) {
            return D8_AUTO;
        }
        return normalizeD8DisplaySource(config.getD8DisplaySource());
    }

    /**
     * @return true → {@code createBillTemplateWorkbook}; false → {@code createCombinedBillWorkbook}
     */
    public boolean useDeptSplitWorkbook(String billLayout, long distinctSheets, long exportRowCount) {
        String layout = normalizeBillLayout(billLayout);
        return switch (layout) {
            case LAYOUT_DEPT_SPLIT -> true;
            case LAYOUT_COMBINED -> false;
            default -> distinctSheets > 1
                    && exportRowCount <= HospitalReconciliationServiceImpl.BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD;
        };
    }

    public boolean preferProgrammaticTemplate(String billLayout, long exportRowCount) {
        String layout = normalizeBillLayout(billLayout);
        return LAYOUT_DEPT_SPLIT.equals(layout)
                && exportRowCount > HospitalReconciliationServiceImpl.BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD;
    }

    public String expectedSheetMode(String billLayout) {
        String layout = normalizeBillLayout(billLayout);
        return switch (layout) {
            case LAYOUT_DEPT_SPLIT -> SHEET_MODE_MULTI_DEPT;
            case LAYOUT_COMBINED -> SHEET_MODE_SINGLE_COMBINED;
            default -> SHEET_MODE_MULTI_DEPT;
        };
    }

    public String buildExportProfileLabel(boolean billingEnabled, String billLayout) {
        String layout = normalizeBillLayout(billLayout);
        String layoutLabel = switch (layout) {
            case LAYOUT_DEPT_SPLIT -> "分科室";
            case LAYOUT_COMBINED -> "合计";
            default -> "自动";
        };
        if (billingEnabled) {
            return "特色导出·" + layoutLabel;
        }
        return "常规导出·" + layoutLabel;
    }
}
