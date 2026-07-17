package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import org.apache.poi.ss.usermodel.Cell;
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

@Component
public class StandardBillExportStrategy implements ExportStrategy {

    static final String[] HEADERS = {
            "发货日期", "单号", "类型", "包类别号", "包名", "包数", "单价", "总价"
    };

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STANDARD_BILL;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        Map<String, List<HospitalReconciliationRow>> bySheet = groupBySheet(context.getRows());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numericStyle = createNumericStyle(workbook);

            if (bySheet.isEmpty()) {
                Sheet sheet = workbook.createSheet("账单");
                writeSheet(sheet, List.of(), context, headerStyle, numericStyle);
            } else {
                for (Map.Entry<String, List<HospitalReconciliationRow>> entry : bySheet.entrySet()) {
                    Sheet sheet = workbook.createSheet(sanitizeSheetName(entry.getKey()));
                    writeSheet(sheet, entry.getValue(), context, headerStyle, numericStyle);
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            String fileName = safeName(context.getHospitalName()) + "_bill_v2_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            return ExportResult.builder()
                    .content(bos.toByteArray())
                    .fileName(fileName)
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .strategyKey(strategyKey())
                    .templateId(context.getTemplate().getTemplateId())
                    .build();
        }
    }

    void writeSheet(
            Sheet sheet,
            List<HospitalReconciliationRow> rows,
            ExportContext context,
            CellStyle headerStyle,
            CellStyle numericStyle) {
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(context.getHospitalName() + " — 灭菌账单");
        Row metaRow = sheet.createRow(1);
        metaRow.createCell(0).setCellValue("账期：" + nullToEmpty(context.getJob().getSourceDateRange()));

        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 4;
        double sheetTotal = 0;
        for (HospitalReconciliationRow row : rows) {
            Row dataRow = sheet.createRow(rowIdx++);
            setCell(dataRow, 0, row.getDeliveryDate());
            setCell(dataRow, 1, row.getOrderNo());
            setCell(dataRow, 2, row.getType());
            setCell(dataRow, 3, row.getCategoryNo());
            setCell(dataRow, 4, row.getPackName());
            setNumeric(dataRow, 5, row.getPackCount(), numericStyle);
            Double unitPrice = row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : row.getUnitPrice();
            setNumeric(dataRow, 6, unitPrice, numericStyle);
            Double total = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            setNumeric(dataRow, 7, total, numericStyle);
            sheetTotal += total != null ? total : 0;
        }

        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(4).setCellValue("合计");
        setNumeric(totalRow, 7, sheetTotal, numericStyle);

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    Map<String, List<HospitalReconciliationRow>> groupBySheet(List<HospitalReconciliationRow> rows) {
        Map<String, List<HospitalReconciliationRow>> map = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : rows) {
            String sheet = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName() : "账单";
            map.computeIfAbsent(sheet, k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createNumericStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    private void setCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void setNumeric(Row row, int col, Number value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0);
        }
        cell.setCellStyle(style);
    }

    String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
