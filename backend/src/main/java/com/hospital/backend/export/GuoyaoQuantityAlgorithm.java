package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

/**
 * 国药总医院汽轮机核算算法（FR-M8-12 / FR-M8-04）。
 * 器械包：总价÷5.5→把数，向下取 15 整倍数，核算数量=(把数÷15×10)+余数。
 * 额外包、敷料包、低温单包装按实际器械数/包数，不适用此算法。
 */
@Component
public class GuoyaoQuantityAlgorithm {

    private static final double HANDLE_UNIT_PRICE = 5.5;
    private static final int HANDLE_BLOCK = 15;

    /**
     * 汽轮机核算：器械包走 FR-M8-04；其余类型保留器械数或包数。
     */
    public int computeQuantity(HospitalReconciliationRow row) {
        if (row == null) {
            return 0;
        }
        if (!isGuoyaoInstrumentPack(row)) {
            Integer instrumentCount = row.getInstrumentCount();
            if (instrumentCount != null && instrumentCount > 0) {
                return instrumentCount;
            }
            Integer packCount = row.getPackCount();
            return packCount != null && packCount > 0 ? packCount : 0;
        }
        Double total = BillExportPriceResolver.resolveTotalPrice(row);
        if (total == null || total <= 0) {
            Integer instrumentCount = row.getInstrumentCount();
            if (instrumentCount != null && instrumentCount > 0) {
                return instrumentCount;
            }
            Integer packCount = row.getPackCount();
            return packCount != null && packCount > 0 ? packCount : 0;
        }
        return computeInstrumentPackQuantity(total);
    }

    /**
     * FR-M8-04：器械包汽轮机核算数量。
     */
    int computeInstrumentPackQuantity(double totalPrice) {
        int handles = (int) Math.floor(totalPrice / HANDLE_UNIT_PRICE);
        if (handles <= 0) {
            return 0;
        }
        int adjusted = (handles / HANDLE_BLOCK) * HANDLE_BLOCK;
        int remainder = handles - adjusted;
        return (adjusted / HANDLE_BLOCK * 10) + remainder;
    }

    boolean isGuoyaoInstrumentPack(HospitalReconciliationRow row) {
        String type = row.getType() != null ? row.getType().replaceAll("\\s+", "") : "";
        if (type.contains("额外包") || type.contains("敷料包")) {
            return false;
        }
        if (type.contains("低温") && type.contains("单包装")) {
            return false;
        }
        return type.contains("器械包");
    }

    /** 将核算结果写回行级导出字段（器械数列）。 */
    public void applyToRow(HospitalReconciliationRow row) {
        if (row == null) {
            return;
        }
        row.setInstrumentCount(computeQuantity(row));
    }
}
