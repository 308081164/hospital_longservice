package com.hospital.backend.export;

import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 附三等：bill export 合并 {@code external_instrument} 表行到「外来器械」sheet（DB 对账行不含租赁器械明细时补齐）。
 */
@Component
@RequiredArgsConstructor
public class ExternalInstrumentBillExportEnricher {

    private static final Set<String> MERGE_CODES = Set.of("ZY3-DIANLI");
    private static final String EXTERNAL_SHEET = "外来器械";

    private final ExternalInstrumentMapper externalInstrumentMapper;

    public List<HospitalReconciliationRow> merge(Long jobId, String customerCode, List<HospitalReconciliationRow> rows) {
        if (jobId == null || customerCode == null || !MERGE_CODES.contains(customerCode)) {
            return rows;
        }
        List<ExternalInstrument> externalRows = externalInstrumentMapper.selectByJobId(jobId);
        if (externalRows == null || externalRows.isEmpty()) {
            return rows;
        }
        List<HospitalReconciliationRow> merged = new ArrayList<>(rows.size() + externalRows.size());
        merged.addAll(rows);
        int rowNo = rows.size() + 1;
        for (ExternalInstrument ext : externalRows) {
            if (Boolean.FALSE.equals(ext.getIsActive())) {
                continue;
            }
            HospitalReconciliationRow row = new HospitalReconciliationRow();
            row.setJobId(jobId);
            row.setRowNumber(rowNo++);
            row.setSheetName(EXTERNAL_SHEET);
            row.setOrderNo(ext.getCategoryNo());
            row.setPackName(ext.getPackName());
            row.setPackCount(0);
            row.setInstrumentCount(ext.getInstrumentCount());
            double unit = ext.getUnitPrice() != null ? ext.getUnitPrice().doubleValue() : 0;
            double total = ext.getTotalAmount() != null
                    ? ext.getTotalAmount().doubleValue()
                    : unit;
            row.setUnitPrice(unit);
            row.setTotalPrice(total);
            row.setCorrectedTotalPrice(total);
            row.setStatus("corrected");
            merged.add(row);
        }
        return merged;
    }
}
