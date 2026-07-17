package com.hospital.backend.export.strategy;

import com.hospital.backend.common.Result;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.service.DailySplitService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DailySplitExportStrategy implements ExportStrategy {

    private final DailySplitService dailySplitService;

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.DAILY_SPLIT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExportResult export(ExportContext context) throws Exception {
        Result<Map<String, Object>> splitResult = dailySplitService.splitJobByDate(context.getJobId());
        if (splitResult.getCode() != 200 || splitResult.getData() == null) {
            throw new IllegalStateException("Daily split failed for job " + context.getJobId());
        }
        Map<String, Object> data = splitResult.getData();
        List<Map<String, Object>> dailyEntries =
                (List<Map<String, Object>>) data.getOrDefault("dailyEntries", List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Sheet sheet = workbook.createSheet("日结拆分");
            int rowIdx = 0;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue(nullToEmpty(context.getHospitalName()) + " — 日结拆分");

            Row meta = sheet.createRow(rowIdx++);
            meta.createCell(0).setCellValue("账期：" + nullToEmpty(context.getJob().getSourceDateRange()));

            Row header = sheet.createRow(rowIdx++);
            String[] cols = {"日期", "行数", "包数", "原总额", "修正总额"};
            for (int i = 0; i < cols.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            for (Map<String, Object> entry : dailyEntries) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(String.valueOf(entry.getOrDefault("deliveryDate", "")));
                writeNumber(row, 1, entry.get("rowCount"));
                writeNumber(row, 2, entry.get("packCount"));
                writeNumber(row, 3, entry.get("originalTotal"));
                writeNumber(row, 4, entry.get("correctedTotal"));
            }

            Row summary = sheet.createRow(rowIdx + 1);
            summary.createCell(0).setCellValue("日合计");
            writeNumber(summary, 4, data.get("dailyCorrectedSum"));
            Row monthly = sheet.createRow(rowIdx + 2);
            monthly.createCell(0).setCellValue("月账修正总额");
            writeNumber(monthly, 4, data.get("monthlyCorrectedTotal"));
            Row reconciled = sheet.createRow(rowIdx + 3);
            reconciled.createCell(0).setCellValue("勾稽状态");
            reconciled.createCell(1).setCellValue(Boolean.TRUE.equals(data.get("reconciled")) ? "通过" : "待核对");

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            String fileName = safeName(context.getHospitalName()) + "_daily_"
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

    private void writeNumber(Row row, int col, Object value) {
        if (value instanceof Number number) {
            row.createCell(col).setCellValue(number.doubleValue());
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
