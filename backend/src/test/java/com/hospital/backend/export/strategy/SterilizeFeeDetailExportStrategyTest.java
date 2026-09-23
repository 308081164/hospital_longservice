package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SterilizeFeeDetailExportStrategyTest {

    private final SterilizeFeeDetailExportStrategy strategy = new SterilizeFeeDetailExportStrategy();

    @Test
    void exportsDiscountedAmountPerPackType() throws Exception {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("额外包(纸塑袋)");
        row.setPackageMaterial("纸塑袋20cm");
        row.setPackCount(2);
        row.setInstrumentCount(10);
        row.setExpectedUnitPrice(10.0);
        row.setStatus("unchanged");

        ExportContext context = ExportContext.builder()
                .jobId(1L)
                .exportType(ExportType.STERILIZE_FEE_DETAIL)
                .job(new HospitalReconciliationJob())
                .rows(List.of(row))
                .template(ResolvedExportTemplate.builder()
                        .exportType(ExportType.STERILIZE_FEE_DETAIL)
                        .strategyKey(ExportTemplateResolverKeys.STERILIZE_FEE_DETAIL)
                        .build())
                .hospitalName("电力测试院")
                .build();

        ExportResult result = strategy.export(context);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getContent()))) {
            var sheet = workbook.getSheetAt(0);
            var dataRow = sheet.getRow(1);
            assertEquals(2.0, dataRow.getCell(2).getNumericCellValue());
            assertEquals(7.0, dataRow.getCell(5).getNumericCellValue());
            assertEquals(14.0, dataRow.getCell(6).getNumericCellValue());
        }
        assertTrue(result.getFileName().contains("sterilize_fee_detail"));
    }
}
