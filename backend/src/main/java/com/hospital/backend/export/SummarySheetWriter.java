package com.hospital.backend.export;

import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.allocation.DepartmentSheetSummary;
import com.hospital.backend.entity.ExternalInstrument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SummarySheetWriter {

    public byte[] buildSingleSheetWorkbook(String sheetName, SheetWriter writer) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet(sheetName);
            writer.write(sheet, headerStyle);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public void writePriceSummarySheet(
            Sheet sheet,
            CellStyle headerStyle,
            AllocationResult allocation,
            String sheetTitle) {
        int rowIdx = 0;
        if (sheetTitle != null && !sheetTitle.isBlank()) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue(sheetTitle);
        }
        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("项目");
        header.createCell(1).setCellValue("金额");
        header.getCell(0).setCellStyle(headerStyle);
        header.getCell(1).setCellStyle(headerStyle);

        if (allocation != null && allocation.getPriceSummaryByCategory() != null) {
            for (Map.Entry<String, Double> entry : allocation.getPriceSummaryByCategory().entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
        }
        if (allocation != null) {
            Row balance = sheet.createRow(rowIdx++);
            balance.createCell(0).setCellValue("勾稽状态");
            balance.createCell(1).setCellValue(allocation.isBalanced() ? "通过" : "待核对");
            Row msg = sheet.createRow(rowIdx);
            msg.createCell(0).setCellValue(nullToEmpty(allocation.getBalanceMessage()));
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    public void writeGrandSummarySheet(
            Sheet sheet,
            CellStyle headerStyle,
            Map<String, Double> categories,
            String sheetTitle) {
        int rowIdx = 0;
        if (sheetTitle != null && !sheetTitle.isBlank()) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue(sheetTitle);
        }
        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("项目");
        header.createCell(1).setCellValue("金额");
        header.getCell(0).setCellStyle(headerStyle);
        header.getCell(1).setCellStyle(headerStyle);

        double total = 0.0;
        for (Map.Entry<String, Double> entry : categories.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
            total += entry.getValue() != null ? entry.getValue() : 0.0;
        }
        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(0).setCellValue("合计");
        totalRow.createCell(1).setCellValue(total);
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    public void writeDepartmentSummariesSheet(
            Sheet sheet,
            CellStyle headerStyle,
            String hospitalName,
            AllocationResult allocation) {
        int rowIdx = 0;
        Row title = sheet.createRow(rowIdx++);
        title.createCell(0).setCellValue(hospitalName + " — 分科室汇总");

        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"科室", "类型", "行数", "包数", "把数", "毛额", "调整额", "净额"};
        for (int i = 0; i < cols.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        if (allocation != null && allocation.getDepartmentSummaries() != null) {
            for (DepartmentSheetSummary summary : allocation.getDepartmentSummaries()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(summary.getDepartmentName());
                row.createCell(1).setCellValue(summary.getSheetType());
                row.createCell(2).setCellValue(summary.getLineCount());
                row.createCell(3).setCellValue(summary.getPackCount());
                row.createCell(4).setCellValue(summary.getInstrumentCount());
                row.createCell(5).setCellValue(summary.getGrossAmount());
                row.createCell(6).setCellValue(summary.getAdjustmentAmount());
                row.createCell(7).setCellValue(summary.getNetAmount());
            }
        }
        autosize(sheet, cols.length);
    }

    public void writeFuyiDeptSummarySheet(
            Sheet sheet,
            CellStyle headerStyle,
            String hospitalName,
            Map<String, DeptFeeRow> deptRows) {
        int rowIdx = 0;
        sheet.createRow(rowIdx++).createCell(0).setCellValue(hospitalName + " — 各科室费用汇总");
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"科室", "灭菌费", "物流分摊", "合计"};
        for (int i = 0; i < cols.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        double grandTotal = 0.0;
        for (DeptFeeRow deptRow : deptRows.values()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(deptRow.department());
            row.createCell(1).setCellValue(deptRow.sterilizeFee());
            row.createCell(2).setCellValue(deptRow.logisticsFee());
            row.createCell(3).setCellValue(deptRow.total());
            grandTotal += deptRow.total();
        }
        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("合计");
        total.createCell(3).setCellValue(grandTotal);
        autosize(sheet, cols.length);
    }

    public void writeInstrumentAuditSheets(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            String hospitalName,
            List<InstrumentAuditRow> pieceRows,
            List<InstrumentAuditRow> instrumentRows,
            List<PackagingAuditRow> packagingRows) throws IOException {
        writeAuditTable(workbook, headerStyle, "包把数", pieceRows,
                new String[]{"类型", "包名", "类别号", "包数", "把数", "金额"});
        writeAuditTable(workbook, headerStyle, "器械把数", instrumentRows,
                new String[]{"类型", "包名", "类别号", "包数", "把数"});
        writePackagingTable(workbook, headerStyle, "灭菌包装", packagingRows);
        workbook.createSheet("说明").createRow(0).createCell(0)
                .setCellValue(hospitalName + " 器械把数表");
    }

    public void writeExternalInstrumentSheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            List<ExternalInstrument> instruments) {
        Sheet sheet = workbook.createSheet("外来器械");
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"科室", "包类别号", "包名", "材料", "患者", "使用日期", "包数", "单价", "合计"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        if (instruments != null) {
            for (ExternalInstrument inst : instruments) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nullToEmpty(inst.getDepartment()));
                row.createCell(1).setCellValue(nullToEmpty(inst.getCategoryNo()));
                row.createCell(2).setCellValue(nullToEmpty(inst.getPackName()));
                row.createCell(3).setCellValue(nullToEmpty(inst.getPackageMaterial()));
                row.createCell(4).setCellValue(nullToEmpty(inst.getPatientName()));
                if (inst.getUsageDate() != null) {
                    row.createCell(5).setCellValue(inst.getUsageDate().toString());
                }
                setInt(row, 6, inst.getPackCount());
                if (inst.getUnitPrice() != null) {
                    row.createCell(7).setCellValue(inst.getUnitPrice().doubleValue());
                }
                if (inst.getTotalAmount() != null) {
                    row.createCell(8).setCellValue(inst.getTotalAmount().doubleValue());
                }
            }
        }
        autosize(sheet, cols.length);
    }

    public void writeFue2PackDataSheet(
            Sheet sheet,
            CellStyle headerStyle,
            String hospitalName,
            List<Fue2PackRow> rows,
            boolean includeAmount) {
        int rowIdx = 0;
        sheet.createRow(rowIdx++).createCell(0).setCellValue(hospitalName + " — 包数据");
        Row header = sheet.createRow(rowIdx++);
        String[] cols = includeAmount
                ? new String[]{"类型", "包名", "类别号", "包数", "把数", "金额"}
                : new String[]{"类型", "包名", "类别号", "包数", "把数"};
        for (int i = 0; i < cols.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (Fue2PackRow row : rows) {
            Row data = sheet.createRow(rowIdx++);
            data.createCell(0).setCellValue(row.type());
            data.createCell(1).setCellValue(row.packName());
            data.createCell(2).setCellValue(row.categoryNo());
            data.createCell(3).setCellValue(row.packCount());
            data.createCell(4).setCellValue(row.instrumentCount());
            if (includeAmount) {
                data.createCell(5).setCellValue(row.amount());
            }
        }
        autosize(sheet, cols.length);
    }

    public void writeLogisticsAllocationSheet(
            Sheet sheet,
            CellStyle headerStyle,
            String hospitalName,
            List<Map<String, Object>> deptAllocations) {
        int rowIdx = 0;
        sheet.createRow(rowIdx++).createCell(0).setCellValue(nullToEmpty(hospitalName) + " — 科室物流分摊");
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"科室", "灭菌费基数", "分摊比例", "分摊物流费"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        if (deptAllocations != null) {
            for (Map<String, Object> item : deptAllocations) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(String.valueOf(item.getOrDefault("department", "")));
                Object sterilizeTotal = item.get("sterilizeTotal");
                if (sterilizeTotal instanceof Number number) {
                    row.createCell(1).setCellValue(number.doubleValue());
                }
                Object ratio = item.get("ratio");
                if (ratio instanceof Number number) {
                    row.createCell(2).setCellValue(number.doubleValue());
                }
                Object allocatedFee = item.get("allocatedFee");
                if (allocatedFee instanceof Number number) {
                    row.createCell(3).setCellValue(number.doubleValue());
                }
            }
        }
        autosize(sheet, cols.length);
    }

    public Map<String, Double> mergeCategoryMaps(Map<String, Double>... maps) {
        Map<String, Double> merged = new LinkedHashMap<>();
        for (Map<String, Double> map : maps) {
            if (map == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : map.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue() != null ? entry.getValue() : 0.0, Double::sum);
            }
        }
        return merged;
    }

    public CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeAuditTable(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            String sheetName,
            List<InstrumentAuditRow> rows,
            String[] cols) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (InstrumentAuditRow auditRow : rows) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(auditRow.type());
            row.createCell(1).setCellValue(auditRow.packName());
            row.createCell(2).setCellValue(auditRow.categoryNo());
            row.createCell(3).setCellValue(auditRow.packCount());
            row.createCell(4).setCellValue(auditRow.instrumentCount());
            if (cols.length > 5) {
                row.createCell(5).setCellValue(auditRow.amount());
            }
        }
        autosize(sheet, cols.length);
    }

    private void writePackagingTable(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            String sheetName,
            List<PackagingAuditRow> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"包装材料", "类型", "包数"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (PackagingAuditRow auditRow : rows) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(auditRow.material());
            row.createCell(1).setCellValue(auditRow.type());
            row.createCell(2).setCellValue(auditRow.packCount());
        }
        autosize(sheet, cols.length);
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void setInt(Row row, int col, Integer value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @FunctionalInterface
    public interface SheetWriter {
        void write(Sheet sheet, CellStyle headerStyle);
    }

    public record InstrumentAuditRow(
            String type, String packName, String categoryNo,
            int packCount, int instrumentCount, double amount) {}

    public record PackagingAuditRow(String material, String type, int packCount) {}

    public record Fue2PackRow(
            String type, String packName, String categoryNo,
            int packCount, int instrumentCount, double amount) {}

    public record DeptFeeRow(String department, double sterilizeFee, double logisticsFee, double total) {}
}
