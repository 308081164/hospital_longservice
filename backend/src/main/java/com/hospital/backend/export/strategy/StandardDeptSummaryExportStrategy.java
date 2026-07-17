package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.allocation.DepartmentSheetSummary;
import com.hospital.backend.common.JsonUtils;
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
import java.text.Collator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StandardDeptSummaryExportStrategy implements ExportStrategy {

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STANDARD_DEPT_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        AllocationResult allocation = parseAllocationResult(context.getJob().getAllocationResult());
        byte[] content;
        if (allocation != null && allocation.getDepartmentSummaries() != null
                && !allocation.getDepartmentSummaries().isEmpty()) {
            content = buildFromAllocation(context, allocation);
        } else {
            content = buildSimpleDeptSummary(context);
        }
        String fileName = safeName(context.getHospitalName()) + "_dept_summary_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private byte[] buildFromAllocation(ExportContext context, AllocationResult allocation) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("分科室汇总");
            int rowIdx = 0;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue(context.getHospitalName() + " — 分科室汇总");

            Row header = sheet.createRow(rowIdx++);
            String[] cols = {"科室", "类型", "行数", "包数", "把数", "毛额", "调整额", "净额"};
            for (int i = 0; i < cols.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

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

            Row balance = sheet.createRow(rowIdx);
            balance.createCell(0).setCellValue("勾稽");
            balance.createCell(1).setCellValue(allocation.isBalanced() ? "通过" : "待核对");
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildSimpleDeptSummary(ExportContext context) throws Exception {
        Map<String, Double> deptSums = new LinkedHashMap<>();
        for (HospitalReconciliationRow row : context.getRows()) {
            String sheet = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName() : "(默认)";
            Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            deptSums.merge(sheet, price != null ? price : 0.0, Double::sum);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(deptSums.entrySet());
        sorted.sort(Map.Entry.comparingByKey(Collator.getInstance(Locale.CHINA)));
        double grandTotal = sorted.stream().mapToDouble(Map.Entry::getValue).sum();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("分科室汇总");
            int rowIdx = 0;
            sheet.createRow(rowIdx++).createCell(0).setCellValue(context.getHospitalName() + " — 分科室汇总");
            Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("科室");
            header.createCell(1).setCellValue("金额");
            header.getCell(0).setCellStyle(headerStyle);
            header.getCell(1).setCellStyle(headerStyle);
            for (Map.Entry<String, Double> entry : sorted) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
            Row total = sheet.createRow(rowIdx);
            total.createCell(0).setCellValue("合计");
            total.createCell(1).setCellValue(grandTotal);
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private AllocationResult parseAllocationResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AllocationResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
