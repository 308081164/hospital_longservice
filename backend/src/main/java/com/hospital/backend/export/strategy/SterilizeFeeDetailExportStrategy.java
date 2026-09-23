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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消毒灭菌费明细表：按消毒方式/包装材料聚合包数、把数、标准单价、折扣单价与金额。
 */
@Component
public class SterilizeFeeDetailExportStrategy implements ExportStrategy {

    private static final double DEFAULT_DISCOUNT_RATE = 0.7;

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STERILIZE_FEE_DETAIL;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        byte[] content = buildWorkbook(context);
        String fileName = safeName(context.getHospitalName()) + "_sterilize_fee_detail_"
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
        double discountRate = DEFAULT_DISCOUNT_RATE;
        Map<String, FeeAggregate> aggregates = new LinkedHashMap<>();

        for (HospitalReconciliationRow row : context.getRows()) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String key = aggregateKey(row);
            FeeAggregate agg = aggregates.computeIfAbsent(key, k -> new FeeAggregate(
                    row.getType() != null ? row.getType() : "未知",
                    row.getPackageMaterial() != null ? row.getPackageMaterial() : "未知"));
            int packCount = row.getPackCount() != null ? row.getPackCount() : 0;
            int instrumentCount = row.getInstrumentCount() != null ? row.getInstrumentCount() : 0;
            Double unitPrice = row.getExpectedUnitPrice() != null
                    ? row.getExpectedUnitPrice()
                    : row.getUnitPrice();
            agg.add(packCount, instrumentCount, unitPrice);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("消毒灭菌费明细");
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            String[] cols = {"消毒方式", "包装材料", "包数", "把数", "标准单价", "折扣单价", "金额"};
            for (int i = 0; i < cols.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }
            for (FeeAggregate agg : aggregates.values()) {
                double standardUnit = agg.standardUnitPrice();
                double discountedUnit = round2(standardUnit * discountRate);
                double amount = round2(discountedUnit * Math.max(1, agg.packCount));
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(agg.sterilizeMethod);
                row.createCell(1).setCellValue(agg.packagingMaterial);
                row.createCell(2).setCellValue(agg.packCount);
                row.createCell(3).setCellValue(agg.instrumentCount);
                row.createCell(4).setCellValue(standardUnit);
                row.createCell(5).setCellValue(discountedUnit);
                row.createCell(6).setCellValue(amount);
            }
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String aggregateKey(HospitalReconciliationRow row) {
        String type = row.getType() != null ? row.getType() : "";
        String material = row.getPackageMaterial() != null ? row.getPackageMaterial() : "";
        return type + "|" + material;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

    private static class FeeAggregate {
        final String sterilizeMethod;
        final String packagingMaterial;
        int packCount;
        int instrumentCount;
        double unitPriceSum;
        int unitPriceSamples;

        FeeAggregate(String sterilizeMethod, String packagingMaterial) {
            this.sterilizeMethod = sterilizeMethod;
            this.packagingMaterial = packagingMaterial;
        }

        void add(int packs, int instruments, Double unitPrice) {
            this.packCount += packs;
            this.instrumentCount += instruments;
            if (unitPrice != null && unitPrice > 0) {
                this.unitPriceSum += unitPrice;
                this.unitPriceSamples++;
            }
        }

        double standardUnitPrice() {
            if (unitPriceSamples <= 0) {
                return 0.0;
            }
            return round2(unitPriceSum / unitPriceSamples);
        }
    }
}
