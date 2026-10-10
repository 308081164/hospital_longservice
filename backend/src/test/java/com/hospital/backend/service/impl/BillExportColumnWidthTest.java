package com.hospital.backend.service.impl;

import com.hospital.backend.export.BillColumnLayout;
import com.hospital.backend.export.BillExportColumnWidths;
import com.hospital.backend.export.BillPrintFooter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发货单汇总表：类型、包名列宽于包数/单价，且打印不按页宽缩小。
 */
class BillExportColumnWidthTest {

    @Test
    void standardBillGivesTypeAndPackNameMoreWidthThanCountAndPrice() throws Exception {
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.STANDARD_8COL)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertTextColumnsWiderThanNumbers(sheet);
            assertThat(widthOf(sheet, "类型"))
                    .isGreaterThan(BillExportColumnWidths.widthUnits(12))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.TYPE_CHARS));
            assertThat(widthOf(sheet, "包名"))
                    .isGreaterThan(BillExportColumnWidths.widthUnits(30))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.PACK_NAME_CHARS));
            BillPrintFooter.apply(sheet);
            assertPrintDoesNotShrinkToPage(sheet);
        }
    }

    @Test
    void fuyiLayoutWidensTypeAndPackNameWithoutChangingHeaders() throws Exception {
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.FUYI_EXTENDED_11COL)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(8);
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("类型");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("包名");
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("包数");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("包装材料");
            assertThat(header.getCell(12).getStringCellValue()).isEqualTo("单价");
            assertThat(header.getCell(13).getStringCellValue()).isEqualTo("总价");
            assertTextColumnsWiderThanNumbers(sheet);
            assertThat(widthOf(sheet, "类型"))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.TYPE_CHARS));
            assertThat(widthOf(sheet, "包名"))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.PACK_NAME_CHARS));
        }
    }

    @Test
    void readableMinimumRestoresTypeAndPackNameAfterNarrowAutoSize() throws Exception {
        try (XSSFWorkbook workbook = HospitalReconciliationServiceImpl.createProgrammaticBillTemplateWorkbook(
                BillColumnLayout.STANDARD_8COL)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            int typeCol = columnOf(sheet.getRow(8), "类型");
            int packCol = columnOf(sheet.getRow(8), "包名");
            sheet.setColumnWidth(typeCol, BillExportColumnWidths.widthUnits(8));
            sheet.setColumnWidth(packCol, BillExportColumnWidths.widthUnits(16));

            BillExportColumnWidths.enforceReadableMinimums(sheet, sheet.getRow(8));

            assertThat(sheet.getColumnWidth(typeCol))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.TYPE_CHARS));
            assertThat(sheet.getColumnWidth(packCol))
                    .isEqualTo(BillExportColumnWidths.widthUnits(BillExportColumnWidths.PACK_NAME_CHARS));
        }
    }

    private static void assertTextColumnsWiderThanNumbers(Sheet sheet) {
        int typeW = widthOf(sheet, "类型");
        int packW = widthOf(sheet, "包名");
        int countW = widthOf(sheet, "包数");
        int priceW = widthOf(sheet, "单价");
        assertThat(typeW).isGreaterThan(countW).isGreaterThan(priceW);
        assertThat(packW).isGreaterThan(countW).isGreaterThan(priceW);
        assertThat(packW).isGreaterThan(typeW);
    }

    private static void assertPrintDoesNotShrinkToPage(XSSFSheet sheet) {
        assertThat(sheet.getFitToPage()).isFalse();
        assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
        assertThat(sheet.getPrintSetup().getScale()).isEqualTo((short) 100);
        assertThat(sheet.getOddFooter().getRight()).isEqualTo(BillPrintFooter.EXCEL_PAGE_LABEL);
        assertThat(sheet.getOddFooter().getLeft()).isBlank();
    }

    private static int widthOf(Sheet sheet, String header) {
        return sheet.getColumnWidth(columnOf(sheet.getRow(8), header));
    }

    private static int columnOf(Row header, String name) {
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null && name.equals(cell.getStringCellValue())) {
                return c;
            }
        }
        throw new AssertionError("缺少表头: " + name);
    }
}
