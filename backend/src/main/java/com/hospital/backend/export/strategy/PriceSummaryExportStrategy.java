package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.SheetOrchestrator;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PriceSummaryExportStrategy implements ExportStrategy {

    private final SheetOrchestrator sheetOrchestrator;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public PriceSummaryExportStrategy(SheetOrchestrator sheetOrchestrator) {
        this.sheetOrchestrator = sheetOrchestrator;
    }

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.PRICE_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        AllocationResult allocation = parseAllocationResult(context.getJob().getAllocationResult());
        byte[] content;
        if (allocation != null && allocation.getPriceSummaryByCategory() != null
                && !allocation.getPriceSummaryByCategory().isEmpty()) {
            content = sheetOrchestrator.buildPriceSummaryWorkbook(context.getHospitalName(), allocation);
        } else {
            content = buildFallbackSummary(context);
        }
        String fileName = safeName(context.getHospitalName()) + "_price_summary_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private byte[] buildFallbackSummary(ExportContext context) throws Exception {
        Map<String, Double> byType = new LinkedHashMap<>();
        context.getRows().forEach(row -> {
            String type = row.getType() != null && !row.getType().isBlank() ? row.getType() : "其他";
            Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            byType.merge(type, price != null ? price : 0.0, Double::sum);
        });
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("价格汇总");
            int rowIdx = 0;
            sheet.createRow(rowIdx++).createCell(0).setCellValue(context.getHospitalName() + " — 价格汇总");
            Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("类型");
            header.createCell(1).setCellValue("金额");
            for (Map.Entry<String, Double> entry : byType.entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
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

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
