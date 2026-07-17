package com.hospital.backend.export.strategy;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardBillExportStrategyTest {

    private final StandardBillExportStrategy strategy = new StandardBillExportStrategy();

    @Test
    void generatesWorkbookWithStandardHeaders() throws Exception {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setSheetName("手术室");
        row.setPackName("测试包");
        row.setPackCount(2);
        row.setExpectedUnitPrice(10.0);
        row.setCorrectedTotalPrice(20.0);

        ExportContext context = ExportContext.builder()
                .jobId(1L)
                .hospitalName("测试医院")
                .job(new HospitalReconciliationJob())
                .rows(List.of(row))
                .template(ResolvedExportTemplate.builder()
                        .strategyKey(ExportTemplateResolverKeys.STANDARD_BILL)
                        .name("default")
                        .build())
                .build();

        ExportResult result = strategy.export(context);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getStrategyKey()).isEqualTo(ExportTemplateResolverKeys.STANDARD_BILL);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.getContent()))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("手术室");
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(0).getStringCellValue()).isEqualTo("发货日期");
        }
    }
}
