package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

/**
 * 国药总医院汽轮机核算算法（FR-M8-12）。
 * 按器械数与包数组合计算导出用量；完整业务规则待 Batch-E UAT 校准。
 */
@Component
public class GuoyaoQuantityAlgorithm {

    /**
     * 汽轮机核算：优先器械数，无器械数时按包数 × 默认系数估算。
     */
    public int computeQuantity(HospitalReconciliationRow row) {
        if (row == null) {
            return 0;
        }
        Integer instrumentCount = row.getInstrumentCount();
        if (instrumentCount != null && instrumentCount > 0) {
            return instrumentCount;
        }
        Integer packCount = row.getPackCount();
        if (packCount != null && packCount > 0) {
            return packCount * defaultPackMultiplier(row);
        }
        return 0;
    }

    /**
     * 高温灭菌包按 1:1，低温/其他按 1.2 系数（可后续由 export_template 配置覆盖）。
     */
    int defaultPackMultiplier(HospitalReconciliationRow row) {
        String type = row.getType();
        if (type != null && (type.contains("低温") || type.contains("LT"))) {
            return 12;
        }
        return 10;
    }

    /** 将核算结果写回行级导出字段（器械数列）。 */
    public void applyToRow(HospitalReconciliationRow row) {
        if (row == null) {
            return;
        }
        row.setInstrumentCount(computeQuantity(row));
    }
}
