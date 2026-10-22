package com.hospital.backend.export;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementLetterTailTest {

    @Test
    void compactedLetterMovesBodyIntoTheNewWideRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("结款函");
            sheet.createRow(16).createCell(3).setCellValue("贵医院各个科室消毒供应中心灭菌费用");
            sheet.addMergedRegion(new CellRangeAddress(16, 16, 3, 8));
            sheet.getRow(16).setHeightInPoints(48);
            sheet.createRow(17).createCell(3).setCellValue("此致");
            sheet.getRow(17).createCell(5).setCellValue("敬礼");
            sheet.createRow(18).createCell(3).setCellValue("哈尔滨铂康");

            SettlementLetterTail.relocateWhenCompacted(sheet, 16, 17, 18);

            assertThat(sheet.getRow(15).getCell(3).getStringCellValue()).contains("贵医院各个科室");
            assertThat(sheet.getRow(15).getHeightInPoints()).isEqualTo(48);
            assertThat(sheet.getRow(16).getCell(3).getStringCellValue()).isEqualTo("此致");
            assertThat(sheet.getRow(16).getCell(5).getStringCellValue()).isEqualTo("敬礼");
            assertThat(sheet.getRow(17).getCell(3).getStringCellValue()).isEqualTo("哈尔滨铂康");
            assertThat(sheet.getRow(17).getCell(5).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(18).getCell(3).getCellType()).isEqualTo(CellType.BLANK);
        }
    }
}
