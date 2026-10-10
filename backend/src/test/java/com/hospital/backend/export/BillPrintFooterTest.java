package com.hospital.backend.export;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillPrintFooterTest {

    @Test
    void excelFooterShowsOnlyPageLabelOnTheRight() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("ICU");
            BillPrintFooter.apply(sheet);

            assertPageOnly(sheet.getOddFooter().getLeft(), sheet.getOddFooter().getCenter(), sheet.getOddFooter().getRight());
            assertPageOnly(sheet.getEvenFooter().getLeft(), sheet.getEvenFooter().getCenter(), sheet.getEvenFooter().getRight());
            assertPageOnly(sheet.getFirstFooter().getLeft(), sheet.getFirstFooter().getCenter(), sheet.getFirstFooter().getRight());
            assertThat(sheet.getPrintSetup().getPaperSize())
                    .isEqualTo(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getPrintSetup().getScale()).isEqualTo((short) 100);
            assertThat(sheet.getFitToPage()).isFalse();
            assertThat(sheet.getMargin(org.apache.poi.ss.usermodel.Sheet.LeftMargin)).isEqualTo(0.3);
            assertThat(sheet.getMargin(org.apache.poi.ss.usermodel.Sheet.RightMargin)).isEqualTo(0.3);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void htmlFooterShowsOnlyPageCounter() {
        String html = BillPrintFooter.htmlBlock();
        assertThat(html).contains("第 <span class=\"page-number\"></span> 页，共 <span class=\"page-total\"></span> 页");
        assertThat(html).doesNotContain("RPTBK-0011");
        assertThat(html).doesNotContain("执行用户");
        assertThat(html).doesNotContain("执行时间");
        assertThat(html).doesNotContain("铂康");
        assertThat(BillPrintFooter.htmlCss()).contains("counter(page)");
        assertThat(BillPrintFooter.htmlCss()).contains("counter(pages)");
    }

    private static void assertPageOnly(String left, String center, String right) {
        assertThat(left == null || left.isBlank()).isTrue();
        assertThat(center == null || center.isBlank()).isTrue();
        assertThat(right).isEqualTo("第 &P 页，共 &N 页");
        assertThat(right).doesNotContain("RPTBK");
        assertThat(right).doesNotContain("执行");
    }
}
