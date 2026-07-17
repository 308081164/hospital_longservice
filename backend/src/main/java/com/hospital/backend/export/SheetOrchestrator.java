package com.hospital.backend.export;

import com.hospital.backend.allocation.AllocatedLineItem;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.allocation.DepartmentSheetSummary;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Multi-sheet export orchestrator for L3 hospitals (市五院):
 * department sheets, fee adjustment, external instruments, price summary.
 */
@Slf4j
@Component
public class SheetOrchestrator {

    public byte[] buildOrchestratedWorkbook(
            String hospitalName,
            List<HospitalReconciliationRow> sourceRows,
            AllocationResult allocation,
            List<ExternalInstrument> externalInstruments) throws IOException {

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            writeDepartmentSummariesSheet(workbook, headerStyle, hospitalName, allocation);
            writeAdjustmentSheet(workbook, headerStyle, allocation);
            writeAllocatedLinesSheet(workbook, headerStyle, allocation);
            writeExternalInstrumentSheet(workbook, headerStyle, externalInstruments);
            writePriceSummarySheet(workbook, headerStyle, allocation);

            Map<String, List<HospitalReconciliationRow>> bySheet = groupRowsBySheet(sourceRows);
            for (Map.Entry<String, List<HospitalReconciliationRow>> entry : bySheet.entrySet()) {
                writeSourceSheet(workbook, headerStyle, entry.getKey(), entry.getValue());
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeDepartmentSummariesSheet(
            XSSFWorkbook workbook, CellStyle headerStyle, String hospitalName, AllocationResult allocation) {
        Sheet sheet = workbook.createSheet("分科室汇总");
        int rowIdx = 0;
        Row title = sheet.createRow(rowIdx++);
        title.createCell(0).setCellValue(hospitalName + " — 分科室汇总");

        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"科室", "类型", "行数", "包数", "把数", "毛额", "调整额", "净额"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
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

    private void writeAdjustmentSheet(XSSFWorkbook workbook, CellStyle headerStyle, AllocationResult allocation) {
        Sheet sheet = workbook.createSheet("费用调整");
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"源Sheet", "行号", "包名", "类别号", "包数", "金额", "原因"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        if (allocation != null && allocation.getAdjustmentLines() != null) {
            for (AllocatedLineItem line : allocation.getAdjustmentLines()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nullToEmpty(line.getSourceSheetName()));
                setInt(row, 1, line.getSourceRowNumber());
                row.createCell(2).setCellValue(nullToEmpty(line.getPackName()));
                row.createCell(3).setCellValue(nullToEmpty(line.getCategoryNo()));
                setInt(row, 4, line.getPackCount());
                setDouble(row, 5, line.getAmount());
                row.createCell(6).setCellValue(nullToEmpty(line.getMatchReason()));
            }
        }
        autosize(sheet, cols.length);
    }

    private void writeAllocatedLinesSheet(XSSFWorkbook workbook, CellStyle headerStyle, AllocationResult allocation) {
        Sheet sheet = workbook.createSheet("科室分配明细");
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"目标科室", "类型", "源Sheet", "行号", "包名", "医生", "金额", "原因"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        if (allocation != null && allocation.getAllocatedLines() != null) {
            for (AllocatedLineItem line : allocation.getAllocatedLines()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nullToEmpty(line.getTargetSheetName()));
                row.createCell(1).setCellValue(nullToEmpty(line.getAllocationType()));
                row.createCell(2).setCellValue(nullToEmpty(line.getSourceSheetName()));
                setInt(row, 3, line.getSourceRowNumber());
                row.createCell(4).setCellValue(nullToEmpty(line.getPackName()));
                row.createCell(5).setCellValue(nullToEmpty(line.getMatchedDoctor()));
                setDouble(row, 6, line.getAmount());
                row.createCell(7).setCellValue(nullToEmpty(line.getMatchReason()));
            }
        }
        autosize(sheet, cols.length);
    }

    private void writeExternalInstrumentSheet(
            XSSFWorkbook workbook, CellStyle headerStyle, List<ExternalInstrument> instruments) {
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

    private void writePriceSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle, AllocationResult allocation) {
        Sheet sheet = workbook.createSheet("总汇总");
        int rowIdx = 0;
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

    private void writeSourceSheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            String sheetName,
            List<HospitalReconciliationRow> rows) {
        String safeName = sheetName.length() > 28 ? sheetName.substring(0, 28) : sheetName;
        Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, safeName));
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"行号", "发货日期", "类型", "类别号", "包名", "包数", "器械数", "单价", "总价", "修正总价"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (HospitalReconciliationRow row : rows) {
            Row data = sheet.createRow(rowIdx++);
            setInt(data, 0, row.getRowNumber());
            data.createCell(1).setCellValue(nullToEmpty(row.getDeliveryDate()));
            data.createCell(2).setCellValue(nullToEmpty(row.getType()));
            data.createCell(3).setCellValue(nullToEmpty(row.getCategoryNo()));
            data.createCell(4).setCellValue(nullToEmpty(row.getPackName()));
            setInt(data, 5, row.getPackCount());
            setInt(data, 6, row.getInstrumentCount());
            setDouble(data, 7, row.getUnitPrice());
            setDouble(data, 8, row.getTotalPrice());
            setDouble(data, 9, row.getCorrectedTotalPrice());
        }
        autosize(sheet, cols.length);
    }

    private Map<String, List<HospitalReconciliationRow>> groupRowsBySheet(List<HospitalReconciliationRow> rows) {
        Map<String, List<HospitalReconciliationRow>> map = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : rows) {
            String key = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName() : "(默认)";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private String uniqueSheetName(XSSFWorkbook workbook, String base) {
        String name = base;
        int suffix = 1;
        while (workbook.getSheet(name) != null) {
            name = base + "_" + suffix++;
        }
        return name;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
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

    private void setDouble(Row row, int col, Double value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public byte[] buildLogisticsAllocationWorkbook(
            String hospitalName,
            List<Map<String, Object>> deptAllocations) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("物流分摊");
            int rowIdx = 0;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue(nullToEmpty(hospitalName) + " — 科室物流分摊");

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
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
