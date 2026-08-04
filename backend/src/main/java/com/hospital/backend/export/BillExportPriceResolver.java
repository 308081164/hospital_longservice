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
        return resolveExportTotal(
                row.getExpectedUnitPrice(),
                row.getUnitPrice(),
                row.getPackCount(),
                row.getCorrectedTotalPrice(),
                row.getTotalPrice());
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
        Double fromExpected = resolveExportTotal(
                row.getExpectedUnitPrice(),
                row.getUnitPrice(),
                row.getPackCount(),
                row.getCorrectedTotalPrice(),
                row.getTotalPrice());
        if (fromExpected != null) {
            return fromExpected;
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
        int perPackInstruments = packs > 1
                ? Math.max(1, (int) Math.round((double) instruments / packs))
                : instruments;
        return round(total / (packs * perPackInstruments));
    }

    /**
     * 导出总价：当 import 阶段未重算 correctedTotal（仍等于原价）但 expectedUnit 已校正时，
     * 用 expectedUnit × packCount，避免 FOLD / fuyi override 行 export 回退原价（附一 7 月 P0）。
     */
    static Double resolveExportTotal(Double expectedUnitPrice,
                                     Double unitPrice,
                                     Integer packCount,
                                     Double correctedTotalPrice,
                                     Double totalPrice) {
        if (expectedUnitPrice == null) {
            if (correctedTotalPrice != null) {
                return correctedTotalPrice;
            }
            return totalPrice;
        }
        int packs = packCount != null ? Math.max(1, packCount) : 1;
        Double recomputed = round(expectedUnitPrice * packs);
        if (correctedTotalPrice == null) {
            return recomputed;
        }
        if (Math.abs(correctedTotalPrice - recomputed) <= 0.001) {
            return correctedTotalPrice;
        }
        boolean correctedMatchesOriginal = totalPrice != null
                && Math.abs(correctedTotalPrice - totalPrice) <= 0.001;
        boolean unitChanged = unitPrice != null
                && Math.abs(expectedUnitPrice - unitPrice) > 0.001;
        if (correctedMatchesOriginal && unitChanged) {
            return recomputed;
        }
        return correctedTotalPrice;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
