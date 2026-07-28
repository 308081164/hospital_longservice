package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class InstrumentAuditExportStrategy implements ExportStrategy {

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.INSTRUMENT_AUDIT;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        byte[] content = buildWorkbook(context);
        String fileName = safeName(context.getHospitalName()) + "_instrument_audit_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private byte[] buildWorkbook(ExportContext context) throws Exception {
        Map<String, PackAggregate> pieceTable = new TreeMap<>();
        Map<String, PackAggregate> instrumentTable = new TreeMap<>();
        Map<String, PackagingAggregate> packagingTable = new TreeMap<>();

        for (HospitalReconciliationRow row : context.getRows()) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String key = aggregateKey(row);
            int packCount = row.getPackCount() != null ? row.getPackCount() : 0;
            int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 0;

            pieceTable.computeIfAbsent(key, k -> new PackAggregate(key, row.getType(), row.getPackName()))
                    .add(packCount, instrumentCount, row.getCorrectedTotalPrice());

            instrumentTable.computeIfAbsent(key, k -> new PackAggregate(key, row.getType(), row.getPackName()))
                    .addInstrumentPieces(packCount, instrumentCount);

            String packagingKey = packagingKey(row);
            packagingTable.computeIfAbsent(packagingKey,
                            k -> new PackagingAggregate(packagingKey, row.getPackageMaterial()))
                    .add(packCount);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            writePackTable(workbook, headerStyle, "把数表", pieceTable, true);
            writePackTable(workbook, headerStyle, "器械量表", instrumentTable, false);
            writePackagingTable(workbook, headerStyle, packagingTable);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writePackTable(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            String sheetName,
            Map<String, PackAggregate> table,
            boolean includeAmount) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        List<String> cols = new ArrayList<>(List.of("类型", "包名", "包数", "器械数"));
        if (includeAmount) {
            cols.add("金额");
        }
        for (int i = 0; i < cols.size(); i++) {
            var cell = header.createCell(i);
            cell.setCellValue(cols.get(i));
            cell.setCellStyle(headerStyle);
        }
        for (PackAggregate agg : table.values()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(nullToEmpty(agg.type));
            row.createCell(1).setCellValue(nullToEmpty(agg.packName));
            row.createCell(2).setCellValue(agg.packCount);
            row.createCell(3).setCellValue(agg.instrumentCount);
            if (includeAmount) {
                row.createCell(4).setCellValue(agg.totalAmount);
            }
        }
        for (int i = 0; i < cols.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writePackagingTable(
            XSSFWorkbook workbook, CellStyle headerStyle, Map<String, PackagingAggregate> table) {
        Sheet sheet = workbook.createSheet("灭菌包装");
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"包装材料", "包数"};
        for (int i = 0; i < cols.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (PackagingAggregate agg : table.values()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(nullToEmpty(agg.material));
            row.createCell(1).setCellValue(agg.packCount);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
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

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
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
