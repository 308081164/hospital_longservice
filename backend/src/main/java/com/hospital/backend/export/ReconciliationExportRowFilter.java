package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 导出前行过滤（D4 省医院等）：排除铂康处理后表不含的占位/零价行，不影响 DB 计价结果。
 */
@Component
public class ReconciliationExportRowFilter {

    private static final Set<String> SHENG_YY_CODES = Set.of("SHENG-YY-NG", "SHENG-YY-XF");

    public List<HospitalReconciliationRow> apply(String customerCode, List<HospitalReconciliationRow> rows) {
        if (rows == null || rows.isEmpty() || customerCode == null || !SHENG_YY_CODES.contains(customerCode)) {
            return rows;
        }
        List<HospitalReconciliationRow> result = new ArrayList<>(rows.size());
        for (HospitalReconciliationRow row : rows) {
            if (shouldIncludeForExport(row)) {
                result.add(row);
            }
        }
        return result;
    }

    boolean shouldIncludeForExport(HospitalReconciliationRow row) {
        if (row == null || "skipped".equalsIgnoreCase(row.getStatus())) {
            return false;
        }
        String packName = row.getPackName();
        if (packName == null || packName.isBlank()) {
            return false;
        }
        Double corrected = BillExportPriceResolver.resolveTotalPrice(row);
        if (corrected != null && Math.abs(corrected) > 0.001) {
            return true;
        }
        Double rawTotal = row.getTotalPrice();
        if (rawTotal != null && Math.abs(rawTotal) > 0.001) {
            return true;
        }
        String type = row.getType() != null ? row.getType() : "";
        String normalizedType = type.replaceAll("\\s+", "");
        if (normalizedType.contains("敷料包") && normalizedType.contains("纸塑袋")) {
            return true;
        }
        return isZeroPriceOverrideRow(row);
    }

    private boolean isZeroPriceOverrideRow(HospitalReconciliationRow row) {
        String rule = row.getPricingRule();
        if (rule == null) {
            return false;
        }
        String lower = rule.toLowerCase(Locale.ROOT);
        return lower.contains("0元") || lower.contains("零价") || lower.contains("仅记录");
    }
}
