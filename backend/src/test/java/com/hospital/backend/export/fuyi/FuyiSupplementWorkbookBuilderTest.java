package com.hospital.backend.export.fuyi;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.LogisticsAllocationService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuyiSupplementWorkbookBuilderTest {

    @Test
    void buildDeptSummaryMatchesFuyiGoldLayout() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江中医药大学附属第一医院");
        job.setSourceDateRange("从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999");

        List<HospitalReconciliationRow> rows = List.of(
                row("手术室(一区)", 43420.8),
                row("耳鼻喉门诊", 34809.2),
                row("透析", 1447.6));

        byte[] bytes = FuyiSupplementWorkbookBuilder.buildDeptSummaryWorkbook(
                job,
                FuyiSupplementWorkbookBuilder.aggregateDeptTotals(rows),
                3987.1,
                2295.0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("汇总");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("黑龙江中医药大学附属第一医院各科室2026年6月1日-2026年6月30日灭菌价格汇总");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("科室");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("价格");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("科室");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("价格");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("手术室(一区)");
            assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(43420.8);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue())
                    .isEqualTo(FuyiDeptSummaryLayout.WASH_FEE_LABEL);
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(3987.1);

            int lastRow = sheet.getLastRowNum();
            assertThat(sheet.getRow(lastRow).getCell(1).getStringCellValue()).isEqualTo("合计");
            assertThat(sheet.getRow(lastRow).getCell(3).getStringCellValue())
                    .contains("元");

            assertThat(hasMergedTitle(sheet)).isTrue();
            assertThat(hasMergedChineseTotal(sheet, lastRow)).isTrue();
            assertThat(!sheet.isDisplayGridlines()).isTrue();
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getBorderTop()).isEqualTo(BorderStyle.THIN);
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getFont().getFontName()).isEqualTo("宋体");
            assertThat(sheet.getRow(2).getCell(2).getCellStyle().getDataFormatString()).contains("0.00");
        }
    }

    @Test
    void buildDeptSummaryInsertsWashFeeRowWhenPresent() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江中医药大学附属第一医院");
        job.setSourceDateRange("从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999");

        byte[] bytes = FuyiSupplementWorkbookBuilder.buildDeptSummaryWorkbook(
                job,
                FuyiSupplementWorkbookBuilder.aggregateDeptTotals(List.of(row("手术室(一区)", 100))),
                3987.1,
                2295.0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("手术室(一区)");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue())
                    .isEqualTo(FuyiDeptSummaryLayout.WASH_FEE_LABEL);
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(3987.1);
        }
    }

    @Test
    void buildDeptSummaryMapsEyeSurgerySheetAliases() throws Exception {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("黑龙江中医药大学附属第一医院");

        List<HospitalReconciliationRow> rows = List.of(
                row("眼科门诊手术室（一）", 448.8),
                row("眼科门诊手术室（二）", 420.8));

        byte[] bytes = FuyiSupplementWorkbookBuilder.buildDeptSummaryWorkbook(
                job,
                FuyiSupplementWorkbookBuilder.aggregateDeptTotals(rows),
                null,
                null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            boolean foundOne = false;
            boolean foundTwo = false;
            for (int rowIdx = 2; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                String leftName = sheet.getRow(rowIdx).getCell(1).getStringCellValue();
                if ("眼科手术室（一）".equals(leftName)) {
                    assertThat(sheet.getRow(rowIdx).getCell(2).getNumericCellValue()).isEqualTo(448.8);
                    foundOne = true;
                }
                if ("眼科手术室（二）".equals(leftName)) {
                    assertThat(sheet.getRow(rowIdx).getCell(2).getNumericCellValue()).isEqualTo(420.8);
                    foundTwo = true;
                }
            }
            assertThat(foundOne).isTrue();
            assertThat(foundTwo).isTrue();
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
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("金额（元）");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("备注");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("耳鼻喉门诊");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("物流分摊");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(112.5);
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue())
                    .contains("灭菌费基数")
                    .contains("占比");
            assertThat(sheet.getRow(0).getCell(0).getCellStyle().getBorderTop()).isEqualTo(BorderStyle.THIN);
            assertThat(!sheet.isDisplayGridlines()).isTrue();
        }
    }

    private static boolean hasMergedTitle(Sheet sheet) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == 0 && region.getLastRow() == 0
                    && region.getFirstColumn() == 1 && region.getLastColumn() == 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMergedChineseTotal(Sheet sheet, int totalRow) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == totalRow && region.getLastRow() == totalRow
                    && region.getFirstColumn() == 3 && region.getLastColumn() == 4) {
                return true;
            }
        }
        return false;
    }

    private static HospitalReconciliationRow row(String sheetName, double total) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setSheetName(sheetName);
        row.setCorrectedTotalPrice(total);
        return row;
    }
}
