package com.hospital.backend.export;

import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * 账单打印页脚：仅页码，如「第1页，共2页」。
 */
public final class BillPrintFooter {

    public static final String EXCEL_PAGE_LABEL = "第 &P 页，共 &N 页";

    private BillPrintFooter() {
    }

    public static String pageLabel() {
        return EXCEL_PAGE_LABEL;
    }

    public static void apply(Sheet sheet) {
        if (sheet == null) {
            return;
        }
        if (sheet instanceof XSSFSheet xssfSheet) {
            fill(xssfSheet.getOddFooter());
            fill(xssfSheet.getEvenFooter());
            fill(xssfSheet.getFirstFooter());
        } else {
            fill(sheet.getFooter());
        }
        // A4 横向、100% 缩放，不用「调整为一页宽」，避免类型/包名加宽后又被整表缩小。
        org.apache.poi.ss.usermodel.PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(true);
        printSetup.setScale((short) 100);
        sheet.setMargin(Sheet.LeftMargin, 0.3);
        sheet.setMargin(Sheet.RightMargin, 0.3);
        if (sheet instanceof XSSFSheet xssfSheet) {
            xssfSheet.setFitToPage(false);
        }
    }

    public static String htmlBlock() {
        return "<div class=\"bill-print-footer\">"
                + "第 <span class=\"page-number\"></span> 页，共 "
                + "<span class=\"page-total\"></span> 页</div>";
    }

    public static String htmlCss() {
        return ".bill-print-footer { position: fixed; right: 0; bottom: 0; "
                + "text-align: right; white-space: nowrap; "
                + "font-size: 11px; line-height: 1.35; font-family: \"SimSun\", \"宋体\", serif; }\n"
                + ".page-number::after { content: counter(page); }\n"
                + ".page-total::after { content: counter(pages); }\n"
                + "@media print { body { padding-bottom: 10mm; } "
                + ".bill-print-footer { position: fixed; right: 0; bottom: 0; } }\n";
    }

    private static void fill(Footer footer) {
        footer.setLeft("");
        footer.setCenter("");
        footer.setRight(pageLabel());
    }
}
