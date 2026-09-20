package com.hospital.backend.export.fuyi;

import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.LogisticsAllocationService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuyiSupplementWorkbookBuilderTest {

    @Test
    void buildDeptSummaryMatchesCustomerSampleLayout() throws Exception {
        List<HospitalReconciliationRow> rows = List.of(
                row("耳鼻喉门诊", 34809.2),
                row("产科门诊236", 210.0),
                row("超声", 147.2));

        byte[] bytes = FuyiSupplementWorkbookBuilder.buildDeptSummaryWorkbook(
                "黑龙江中医药大学附属第一医院", rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo(FuyiSupplementWorkbookBuilder.DEPT_SUMMARY_SHEET);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("黑龙江中医药大学附属第一医院 — 分科室汇总");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("科室");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("金额");

            int lastRow = sheet.getLastRowNum();
            assertThat(sheet.getRow(lastRow).getCell(0).getStringCellValue()).isEqualTo("合计");
            assertThat(sheet.getRow(lastRow).getCell(1).getNumericCellValue()).isEqualTo(35166.4);

            assertThat(sheet.getColumnWidth(0)).isEqualTo((int) (21.38671875 * 256));
            assertThat(sheet.getColumnWidth(1)).isEqualTo((int) (9.39453125 * 256));
        }
    }

    @Test
    void buildLogisticsAllocationMatchesCustomerSampleLayout() throws Exception {
        List<LogisticsAllocationService.DeptAllocation> departments = List.of(
                new LogisticsAllocationService.DeptAllocation("耳鼻喉门诊", 34809.2, 0.25, 112.5),
                new LogisticsAllocationService.DeptAllocation("超声", 147.2, 0.05, 22.5));

        byte[] bytes = FuyiSupplementWorkbookBuilder.buildLogisticsAllocationWorkbook(departments);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo(FuyiSupplementWorkbookBuilder.LOGISTICS_SHEET);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("科室");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("类型");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("金额");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("备注");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("耳鼻喉门诊");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("物流分摊");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(112.5);
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue())
                    .contains("灭菌费基数")
                    .contains("占比");
        }
    }

    private static HospitalReconciliationRow row(String sheetName, double total) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setSheetName(sheetName);
        row.setCorrectedTotalPrice(total);
        return row;
    }
}
