package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.InstrumentAuditDataBuilder;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.service.InstrumentAuditReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstrumentAuditReportServiceImpl implements InstrumentAuditReportService {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final InstrumentAuditDataBuilder auditDataBuilder;

    @Override
    public Result<Map<String, Object>> buildAuditReport(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "对账任务不存在");
        }

        List<HospitalReconciliationRow> rows = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        InstrumentAuditDataBuilder.InstrumentAuditData data = auditDataBuilder.build(rows);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("jobId", jobId);
        report.put("hospitalName", job.getHospitalName());
        report.put("totalPacks", data.totalPacks());
        report.put("totalInstruments", data.totalInstruments());
        report.put("pieceCountTable", toRows(data.pieceRows()));
        report.put("instrumentCountTable", toRows(data.instrumentRows()));
        report.put("sterilizationPackagingTable", toPackagingRows(data.packagingRows()));
        report.put("reconciliationCheck", Map.of(
                "billTotalPacks", data.totalPacks(),
                "pieceTableSum", sumPacks(data.pieceRows()),
                "consistent", data.totalPacks() == sumPacks(data.pieceRows())
        ));
        return Result.success(report);
    }

    private List<Map<String, Object>> toRows(List<com.hospital.backend.export.SummarySheetWriter.InstrumentAuditRow> table) {
        return table.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", row.type());
            map.put("packName", row.packName());
            map.put("categoryNo", row.categoryNo());
            map.put("packCount", row.packCount());
            map.put("instrumentCount", row.instrumentCount());
            map.put("totalAmount", row.amount());
            return map;
        }).toList();
    }

    private List<Map<String, Object>> toPackagingRows(
            List<com.hospital.backend.export.SummarySheetWriter.PackagingAuditRow> table) {
        return table.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("packageMaterial", row.material());
            map.put("type", row.type());
            map.put("packCount", row.packCount());
            return map;
        }).toList();
    }

    private int sumPacks(List<com.hospital.backend.export.SummarySheetWriter.InstrumentAuditRow> table) {
        return table.stream().mapToInt(com.hospital.backend.export.SummarySheetWriter.InstrumentAuditRow::packCount).sum();
    }
}
