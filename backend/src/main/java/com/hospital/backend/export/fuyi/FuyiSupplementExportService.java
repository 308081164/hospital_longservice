package com.hospital.backend.export.fuyi;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.LogisticsAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 分科室汇总 / 物流分摊附表导出（对齐客户确认包样表版式）。
 */
@Service
@RequiredArgsConstructor
public class FuyiSupplementExportService {

    private final FuyiSupplementExportSupport exportSupport;

    public byte[] exportDeptSummary(HospitalReconciliationJob job, List<HospitalReconciliationRow> rows)
            throws IOException {
        return FuyiSupplementWorkbookBuilder.buildDeptSummaryWorkbook(
                job,
                FuyiSupplementWorkbookBuilder.aggregateDeptTotals(rows),
                exportSupport.resolveWashFee(job),
                exportSupport.resolveLogisticsFee(job));
    }

    public byte[] exportLogisticsAllocation(HospitalReconciliationJob job, List<HospitalReconciliationRow> rows)
            throws IOException {
        LogisticsAllocationService.AllocationResult allocation =
                exportSupport.resolveLogisticsAllocation(job, rows);
        return FuyiSupplementWorkbookBuilder.buildLogisticsAllocationWorkbook(allocation.departments());
    }
}
