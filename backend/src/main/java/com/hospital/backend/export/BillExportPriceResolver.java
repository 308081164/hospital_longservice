package com.hospital.backend.export;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.entity.HospitalReconciliationRow;

/**
 * 账单导出单价/总价解析：优先使用计价引擎修正结果，避免 export 回退到原始账单单价（D2/D5）。
 */
public final class BillExportPriceResolver {

    private BillExportPriceResolver() {
    }

    public static Double resolveUnitPrice(BillRowItem row) {
        if (row == null) {
            return null;
        }
        Double total = resolveTotalPrice(row);
        Integer packCount = row.getPackCount();
        if (total != null && packCount != null && packCount > 0) {
            return round(total / packCount);
        }
        if (row.getExpectedUnitPrice() != null) {
            return row.getExpectedUnitPrice();
        }
        return row.getUnitPrice();
    }

    public static Double resolveTotalPrice(BillRowItem row) {
        if (row == null) {
            return null;
        }
        if (row.getCorrectedTotalPrice() != null) {
            return row.getCorrectedTotalPrice();
        }
        if (row.getExpectedUnitPrice() != null) {
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            return round(row.getExpectedUnitPrice() * packCount);
        }
        return row.getTotalPrice();
    }

    public static Double resolveUnitPrice(HospitalReconciliationRow row) {
        if (row == null) {
            return null;
        }
        Double total = resolveTotalPrice(row);
        Integer packCount = row.getPackCount();
        if (total != null && packCount != null && packCount > 0) {
            return round(total / packCount);
        }
        if (row.getExpectedUnitPrice() != null) {
            return row.getExpectedUnitPrice();
        }
        return row.getUnitPrice();
    }

    public static Double resolveTotalPrice(HospitalReconciliationRow row) {
        if (row == null) {
            return null;
        }
        if (row.getCorrectedTotalPrice() != null) {
            return row.getCorrectedTotalPrice();
        }
        if (row.getExpectedUnitPrice() != null) {
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            return round(row.getExpectedUnitPrice() * packCount);
        }
        return row.getTotalPrice();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
