package com.hospital.backend.export;

import com.hospital.backend.allocation.AllocatedLineItem;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Multi-sheet export orchestrator for L3 hospitals (市五院):
 * department sheets, fee adjustment, external instruments, price summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SheetOrchestrator {

    private final SummarySheetWriter summarySheetWriter;

    public byte[] buildOrchestratedWorkbook(
            String hospitalName,
            List<HospitalReconciliationRow> sourceRows,
            AllocationResult allocation,
            List<ExternalInstrument> externalInstruments) throws IOException {

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = summarySheetWriter.createHeaderStyle(workbook);

            writeDepartmentSummariesSheet(workbook, headerStyle, hospitalName, allocation);
            writeAdjustmentSheet(workbook, headerStyle, allocation);
            writeAllocatedLinesSheet(workbook, headerStyle, allocation);
            summarySheetWriter.writeExternalInstrumentSheet(workbook, headerStyle, externalInstruments);
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
        summarySheetWriter.writeDepartmentSummariesSheet(sheet, headerStyle, hospitalName, allocation);
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

    private void writePriceSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle, AllocationResult allocation) {
        Sheet sheet = workbook.createSheet("总汇总");
        summarySheetWriter.writePriceSummarySheet(sheet, headerStyle, allocation, null);
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
        return summarySheetWriter.buildSingleSheetWorkbook("物流分摊", (sheet, headerStyle) ->
                summarySheetWriter.writeLogisticsAllocationSheet(
                        sheet, headerStyle, hospitalName, deptAllocations));
    }
}
