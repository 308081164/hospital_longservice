package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class InstrumentAuditDataBuilder {

    public InstrumentAuditData build(List<HospitalReconciliationRow> rows) {
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

            pieceTable.computeIfAbsent(key, k -> new PackAggregate(row.getType(), row.getPackName(), row.getCategoryNo()))
                    .add(packCount, instrumentCount, row.getCorrectedTotalPrice());

            instrumentTable.computeIfAbsent(key, k -> new PackAggregate(row.getType(), row.getPackName(), row.getCategoryNo()))
                    .addInstrumentPieces(packCount, instrumentCount);

            String packagingKey = packagingKey(row);
            packagingTable.computeIfAbsent(packagingKey,
                            k -> new PackagingAggregate(row.getPackageMaterial(), row.getType()))
                    .add(packCount);
        }

        return new InstrumentAuditData(
                totalPacks,
                totalInstruments,
                toAuditRows(pieceTable),
                toAuditRows(instrumentTable),
                toPackagingRows(packagingTable));
    }

    public List<SummarySheetWriter.Fue2PackRow> buildFue2PackRows(List<HospitalReconciliationRow> rows) {
        Map<String, PackAggregate> table = new TreeMap<>();
        for (HospitalReconciliationRow row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String key = aggregateKey(row);
            int packCount = row.getPackCount() != null ? row.getPackCount() : 0;
            int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 0;
            table.computeIfAbsent(key, k -> new PackAggregate(row.getType(), row.getPackName(), row.getCategoryNo()))
                    .add(packCount, instrumentCount, row.getCorrectedTotalPrice());
        }
        List<SummarySheetWriter.Fue2PackRow> result = new ArrayList<>();
        for (PackAggregate agg : table.values()) {
            result.add(new SummarySheetWriter.Fue2PackRow(
                    nullToEmpty(agg.type),
                    nullToEmpty(agg.packName),
                    nullToEmpty(agg.categoryNo),
                    agg.packCount,
                    agg.instrumentCount,
                    agg.totalAmount));
        }
        return result;
    }

    public Map<String, SummarySheetWriter.DeptFeeRow> buildFuyiDeptRows(List<HospitalReconciliationRow> rows) {
        Map<String, DeptAccumulator> accumulators = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String dept = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName() : "(默认)";
            Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            double amount = price != null ? price : 0.0;
            accumulators.computeIfAbsent(dept, k -> new DeptAccumulator()).addSterilize(amount);
        }
        Map<String, SummarySheetWriter.DeptFeeRow> result = new LinkedHashMap<>();
        for (Map.Entry<String, DeptAccumulator> entry : accumulators.entrySet()) {
            DeptAccumulator acc = entry.getValue();
            result.put(entry.getKey(), new SummarySheetWriter.DeptFeeRow(
                    entry.getKey(), acc.sterilizeFee, 0.0, acc.sterilizeFee));
        }
        return result;
    }

    private List<SummarySheetWriter.InstrumentAuditRow> toAuditRows(Map<String, PackAggregate> table) {
        List<SummarySheetWriter.InstrumentAuditRow> list = new ArrayList<>();
        for (PackAggregate agg : table.values()) {
            list.add(new SummarySheetWriter.InstrumentAuditRow(
                    nullToEmpty(agg.type),
                    nullToEmpty(agg.packName),
                    nullToEmpty(agg.categoryNo),
                    agg.packCount,
                    agg.instrumentCount,
                    agg.totalAmount));
        }
        return list;
    }

    private List<SummarySheetWriter.PackagingAuditRow> toPackagingRows(Map<String, PackagingAggregate> table) {
        List<SummarySheetWriter.PackagingAuditRow> list = new ArrayList<>();
        for (PackagingAggregate agg : table.values()) {
            list.add(new SummarySheetWriter.PackagingAuditRow(
                    nullToEmpty(agg.material),
                    nullToEmpty(agg.type),
                    agg.packCount));
        }
        return list;
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

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public record InstrumentAuditData(
            int totalPacks,
            int totalInstruments,
            List<SummarySheetWriter.InstrumentAuditRow> pieceRows,
            List<SummarySheetWriter.InstrumentAuditRow> instrumentRows,
            List<SummarySheetWriter.PackagingAuditRow> packagingRows) {}

    private static class PackAggregate {
        final String type;
        final String packName;
        final String categoryNo;
        int packCount;
        int instrumentCount;
        double totalAmount;

        PackAggregate(String type, String packName, String categoryNo) {
            this.type = type;
            this.packName = packName;
            this.categoryNo = categoryNo;
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
        final String material;
        final String type;
        int packCount;

        PackagingAggregate(String material, String type) {
            this.material = material;
            this.type = type;
        }

        void add(int packs) {
            this.packCount += packs;
        }
    }

    private static class DeptAccumulator {
        double sterilizeFee;

        void addSterilize(double amount) {
            sterilizeFee += amount;
        }
    }
}
