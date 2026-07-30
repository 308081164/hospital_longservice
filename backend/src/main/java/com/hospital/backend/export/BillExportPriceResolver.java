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

    /**
     * L 列「单价（把）」：优先保留原始导入把价，否则由校正总价按包数×器械数反推。
     */
    public static Double resolvePerPiecePrice(BillRowItem row) {
        if (row == null) {
            return null;
        }
        int instruments = row.getInstrumentCount() != null ? Math.max(1, row.getInstrumentCount()) : 1;
        Double derived = derivePerPieceFromTotals(row.getPackCount(), row.getInstrumentCount(),
                resolveTotalPrice(row));
        if (instruments > 1 && derived != null) {
            return derived;
        }
        Double importPrice = readImportUnitPrice(row.getOriginal());
        if (importPrice != null) {
            return importPrice;
        }
        if (derived != null) {
            return derived;
        }
        return resolveUnitPrice(row);
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
        // D2/D6/D8：计价引擎 expectedUnitPrice 优先于 matchedPriceOption（后者常为原始器械单价）
        if (row.getExpectedUnitPrice() != null) {
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            return round(row.getExpectedUnitPrice() * packCount);
        }
        if (row.getMatchedPriceOption() != null) {
            int packCount = row.getPackCount() != null ? Math.max(1, row.getPackCount()) : 1;
            return round(row.getMatchedPriceOption() * packCount);
        }
        return row.getTotalPrice();
    }

    public static Double resolvePerPiecePrice(HospitalReconciliationRow row) {
        if (row == null) {
            return null;
        }
        int instruments = row.getInstrumentCount() != null ? Math.max(1, row.getInstrumentCount()) : 1;
        Double derived = derivePerPieceFromTotals(row.getPackCount(), row.getInstrumentCount(),
                resolveTotalPrice(row));
        if (instruments > 1 && derived != null) {
            return derived;
        }
        if (row.getUnitPrice() != null) {
            return row.getUnitPrice();
        }
        if (derived != null) {
            return derived;
        }
        return resolveUnitPrice(row);
    }

    private static Double readImportUnitPrice(java.util.Map<String, Object> original) {
        if (original == null) {
            return null;
        }
        Object value = original.get("importUnitPrice");
        if (value instanceof Number number) {
            return round(number.doubleValue());
        }
        return null;
    }

    private static Double derivePerPieceFromTotals(Integer packCount, Integer instrumentCount, Double total) {
        if (total == null) {
            return null;
        }
        int packs = packCount != null ? Math.max(1, packCount) : 1;
        int instruments = instrumentCount != null ? Math.max(1, instrumentCount) : 1;
        return round(total / (packs * instruments));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
