package com.hospital.backend.export.strategy;

import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.SettlementTemplateFiller;
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

@Component
public class StandardSettlementExportStrategy implements ExportStrategy {

    private final SettlementTemplateFiller settlementTemplateFiller;

    public StandardSettlementExportStrategy(SettlementTemplateFiller settlementTemplateFiller) {
        this.settlementTemplateFiller = settlementTemplateFiller;
    }

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.STANDARD_SETTLEMENT;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        double sterilizeTotal = context.getJob().getCorrectedTotalPrice() != null
                ? context.getJob().getCorrectedTotalPrice()
                : sumRowTotals(context);
        List<SettlementTemplateFiller.SettlementFeeRow> feeRows =
                settlementTemplateFiller.buildFeeRows(context.getJob(), sterilizeTotal);
        double total = settlementTemplateFiller.computeTotalAmount(feeRows);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("结款函");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("结款通知函");
            Row hospital = sheet.createRow(2);
            hospital.createCell(0).setCellValue("致：" + nullToEmpty(context.getHospitalName()));
            Row period = sheet.createRow(3);
            period.createCell(0).setCellValue("结算期间：" + nullToEmpty(context.getJob().getSourceDateRange()));

            Row header = sheet.createRow(5);
            String[] cols = {"序号", "费用项目", "金额（元）", "备注"};
            for (int i = 0; i < cols.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 6;
            for (SettlementTemplateFiller.SettlementFeeRow feeRow : feeRows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(feeRow.getSequence());
                row.createCell(1).setCellValue(feeRow.getItemName());
                row.createCell(2).setCellValue(feeRow.getAmount());
                row.createCell(3).setCellValue(feeRow.getRemark() != null ? feeRow.getRemark() : "");
            }

            Row totalRow = sheet.createRow(rowIdx + 1);
            totalRow.createCell(1).setCellValue("合计");
            totalRow.createCell(2).setCellValue(total);

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            String fileName = safeName(context.getHospitalName()) + "_settlement_v2_"
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

    private double sumRowTotals(ExportContext context) {
        return context.getRows().stream()
                .mapToDouble(r -> {
                    Double v = r.getCorrectedTotalPrice() != null ? r.getCorrectedTotalPrice() : r.getTotalPrice();
                    return v != null ? v : 0;
                })
                .sum();
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
