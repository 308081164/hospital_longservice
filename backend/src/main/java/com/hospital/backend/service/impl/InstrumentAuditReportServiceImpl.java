package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.service.InstrumentAuditReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class InstrumentAuditReportServiceImpl implements InstrumentAuditReportService {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;

    @Override
    public Result<Map<String, Object>> buildAuditReport(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "对账任务不存在");
        }

        List<HospitalReconciliationRow> rows = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        Map<String, PackAggregate> pieceTable = new TreeMap<>();
        Map<String, PackAggregate> instrumentTable = new TreeMap<>();
        Map<String, PackagingAggregate> packagingTable = new TreeMap<>();

        int totalPacks = 0;
        int totalInstruments = 0;

        for (HospitalReconciliationRow row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String key = aggregateKey(row);
            int packCount = row.getPackCount() != null ? row.getPackCount() : 0;
            int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 0;
            totalPacks += packCount;
            totalInstruments += instrumentCount;

            pieceTable.computeIfAbsent(key, k -> new PackAggregate(key, row.getType(), row.getPackName()))
                    .add(packCount, instrumentCount, row.getCorrectedTotalPrice());

            instrumentTable.computeIfAbsent(key, k -> new PackAggregate(key, row.getType(), row.getPackName()))
                    .addInstrumentPieces(packCount, instrumentCount);

            String packagingKey = packagingKey(row);
            packagingTable.computeIfAbsent(packagingKey, k -> new PackagingAggregate(packagingKey, row.getPackageMaterial()))
                    .add(packCount);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("jobId", jobId);
        report.put("hospitalName", job.getHospitalName());
        report.put("totalPacks", totalPacks);
        report.put("totalInstruments", totalInstruments);
        report.put("pieceCountTable", toRows(pieceTable));
        report.put("instrumentCountTable", toRows(instrumentTable));
        report.put("sterilizationPackagingTable", toPackagingRows(packagingTable));
        report.put("reconciliationCheck", Map.of(
                "billTotalPacks", totalPacks,
                "pieceTableSum", sumPacks(pieceTable),
                "consistent", totalPacks == sumPacks(pieceTable)
        ));
        return Result.success(report);
    }

    private static String aggregateKey(HospitalReconciliationRow row) {
        String type = row.getType() != null ? row.getType() : "";
        String pack = row.getPackName() != null ? row.getPackName() : "";
        String cat = row.getCategoryNo() != null ? row.getCategoryNo() : "";
        return type + "|" + pack + "|" + cat;
    }

    private static String packagingKey(HospitalReconciliationRow row) {
        String material = row.getPackageMaterial() != null ? row.getPackageMaterial() : "未知";
        String type = row.getType() != null ? row.getType() : "";
        return material + "|" + type;
    }

    private List<Map<String, Object>> toRows(Map<String, PackAggregate> table) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PackAggregate agg : table.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", agg.key);
            row.put("type", agg.type);
            row.put("packName", agg.packName);
            row.put("packCount", agg.packCount);
            row.put("instrumentCount", agg.instrumentCount);
            row.put("totalAmount", agg.totalAmount);
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> toPackagingRows(Map<String, PackagingAggregate> table) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PackagingAggregate agg : table.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("packagingKey", agg.key);
            row.put("packageMaterial", agg.material);
            row.put("packCount", agg.packCount);
            list.add(row);
        }
        return list;
    }

    private int sumPacks(Map<String, PackAggregate> table) {
        return table.values().stream().mapToInt(a -> a.packCount).sum();
    }

    private static class PackAggregate {
        final String key;
        final String type;
        final String packName;
        int packCount;
        int instrumentCount;
        double totalAmount;

        PackAggregate(String key, String type, String packName) {
            this.key = key;
            this.type = type;
            this.packName = packName;
        }

        void add(int packs, int instruments, Double amount) {
            this.packCount += packs;
            this.instrumentCount += instruments;
            if (amount != null) {
                this.totalAmount += amount;
            }
        }

        void addInstrumentPieces(int packs, int instruments) {
            this.packCount += packs;
            this.instrumentCount += instruments;
        }
    }

    private static class PackagingAggregate {
        final String key;
        final String material;
        int packCount;

        PackagingAggregate(String key, String material) {
            this.key = key;
            this.material = material;
        }

        void add(int packs) {
            this.packCount += packs;
        }
    }
}
