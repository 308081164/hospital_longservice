package com.hospital.backend.export.fuyi;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.BillExportPriceResolver;
import com.hospital.backend.export.ChineseAmountFormatter;
import com.hospital.backend.export.SettlementPeriodFormatter;
import com.hospital.backend.export.SettlementPeriodResolver;
import com.hospital.backend.service.LogisticsAllocationService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中医附一（ZYY-D1）分科室汇总 / 物流分摊附表。
 *
 * <p>分科室汇总对齐金标准：{@code 测试用例/黑龙江中医药大学附属第一医院/处理后表格/6月__附一6月各科室费用汇总.xlsx}
 */
public final class FuyiSupplementWorkbookBuilder {

    public static final String DEPT_SUMMARY_SHEET = FuyiDeptSummaryLayout.SHEET_NAME;
    public static final String LOGISTICS_SHEET = "物流分摊";

    private static final String FONT_NAME = "宋体";
    private static final short TITLE_FONT_SIZE = 12;
    private static final short BODY_FONT_SIZE = 12;
    private static final String AMOUNT_FORMAT = "0.00";

    private FuyiSupplementWorkbookBuilder() {
    }

    public static Map<String, Double> aggregateDeptTotals(List<HospitalReconciliationRow> rows) {
        Map<String, Double> deptSums = new LinkedHashMap<>();
        if (rows == null) {
            return deptSums;
        }
        for (HospitalReconciliationRow row : rows) {
            String sheet = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName().trim()
                    : "(默认)";
            String canonical = FuyiDeptSummaryLayout.canonicalDeptName(sheet);
            deptSums.merge(canonical, resolveExportRowTotal(row), Double::sum);
        }
        Map<String, Double> rounded = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : deptSums.entrySet()) {
            rounded.put(entry.getKey(), roundCurrency(entry.getValue()));
        }
        return rounded;
    }

    static double resolveExportRowTotal(HospitalReconciliationRow row) {
        Double total = BillExportPriceResolver.resolveTotalPrice(row);
        if (total != null) {
            return total;
        }
        if (row.getCorrectedTotalPrice() != null) {
            return row.getCorrectedTotalPrice();
        }
        return row.getTotalPrice() != null ? row.getTotalPrice() : 0.0;
    }

    public static List<Map.Entry<String, Double>> sortDeptTotals(Map<String, Double> deptSums) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(deptSums.entrySet());
        Collator collator = Collator.getInstance(Locale.CHINA);
        sorted.sort(Map.Entry.comparingByKey(collator));
        return sorted;
    }

    public static byte[] buildDeptSummaryWorkbook(
            HospitalReconciliationJob job,
            Map<String, Double> deptTotals,
            Double washFee,
            Double logisticsFee) throws IOException {
        String hospitalName = job != null && job.getHospitalName() != null && !job.getHospitalName().isBlank()
                ? job.getHospitalName().trim()
                : "医院";
        FuyiDeptSummaryLayout.Layout layout = FuyiDeptSummaryLayout.build(deptTotals, washFee, logisticsFee);
        String title = SettlementPeriodResolver.resolve(job)
                .map(period -> SettlementPeriodFormatter.formatDeptSummaryTitle(hospitalName, period))
                .orElse(hospitalName + "各科室灭菌价格汇总");

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(DEPT_SUMMARY_SHEET);
            sheet.setDisplayGridlines(false);
            applyColumnWidths(sheet);

            TableStyles styles = TableStyles.create(workbook);
            int rowIdx = 0;

            XSSFRow titleRow = sheet.createRow(rowIdx++);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 4));
            setCell(titleRow, 1, title, styles.titleStyle());
            for (int col = 0; col <= 4; col++) {
                if (col != 1) {
                    setCell(titleRow, col, "", styles.emptyStyle());
                }
            }

            XSSFRow headerRow = sheet.createRow(rowIdx++);
            setCell(headerRow, 0, "", styles.emptyStyle());
            String[] headers = {"科室", "价格", "科室", "价格"};
            for (int i = 0; i < headers.length; i++) {
                setCell(headerRow, i + 1, headers[i], styles.headerStyle());
            }

            int dataRows = Math.max(layout.left().size(), layout.right().size());
            for (int i = 0; i < dataRows; i++) {
                XSSFRow dataRow = sheet.createRow(rowIdx++);
                setCell(dataRow, 0, "", styles.emptyStyle());
                if (i < layout.left().size()) {
                    FuyiDeptSummaryLayout.DeptEntry left = layout.left().get(i);
                    setCell(dataRow, 1, left.department(), styles.bodyTextStyle());
                    setOptionalAmountCell(dataRow, 2, left.amount(), styles.bodyAmountStyle());
                } else {
                    setCell(dataRow, 1, "", styles.bodyTextStyle());
                    setCell(dataRow, 2, "", styles.bodyAmountStyle());
                }
                if (i < layout.right().size()) {
                    FuyiDeptSummaryLayout.DeptEntry right = layout.right().get(i);
                    setCell(dataRow, 3, right.department(), styles.bodyTextStyle());
                    setOptionalAmountCell(dataRow, 4, right.amount(), styles.bodyAmountStyle());
                } else {
                    setCell(dataRow, 3, "", styles.bodyTextStyle());
                    setCell(dataRow, 4, "", styles.bodyAmountStyle());
                }
            }

            XSSFRow totalRow = sheet.createRow(rowIdx);
            setCell(totalRow, 0, "", styles.emptyStyle());
            setCell(totalRow, 1, "合计", styles.headerStyle());
            setAmountCell(totalRow, 2, layout.grandTotal(), styles.bodyAmountStyle());
            setCell(totalRow, 3, ChineseAmountFormatter.format(layout.grandTotal()), styles.bodyTextStyle());
            setCell(totalRow, 4, "", styles.mergedRightStyle());
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 3, 4));

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] buildDeptSummaryWorkbook(String hospitalName, List<HospitalReconciliationRow> rows)
            throws IOException {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName(hospitalName);
        return buildDeptSummaryWorkbook(job, aggregateDeptTotals(rows), null, null);
    }

    public static byte[] buildLogisticsAllocationWorkbook(
            List<LogisticsAllocationService.DeptAllocation> departments) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(LOGISTICS_SHEET);
            sheet.setDisplayGridlines(false);

            TableStyles styles = TableStyles.create(workbook);
            int rowIdx = 0;

            XSSFRow headerRow = sheet.createRow(rowIdx++);
            setCell(headerRow, 0, "科室", styles.headerStyle());
            setCell(headerRow, 1, "类型", styles.headerStyle());
            setCell(headerRow, 2, "金额（元）", styles.headerStyle());
            setCell(headerRow, 3, "备注", styles.headerStyle());

            if (departments != null) {
                List<LogisticsAllocationService.DeptAllocation> sorted = new ArrayList<>(departments);
                Collator collator = Collator.getInstance(Locale.CHINA);
                sorted.sort((a, b) -> collator.compare(a.department(), b.department()));
                for (LogisticsAllocationService.DeptAllocation dept : sorted) {
                    if (Math.abs(dept.allocatedFee()) < 0.005) {
                        continue;
                    }
                    XSSFRow row = sheet.createRow(rowIdx++);
                    setCell(row, 0, dept.department(), styles.bodyTextStyle());
                    setCell(row, 1, "物流分摊", styles.bodyTextStyle());
                    setAmountCell(row, 2, dept.allocatedFee(), styles.bodyAmountStyle());
                    setCell(row, 3, buildLogisticsRemark(dept), styles.bodyTextStyle());
                }
            }

            autosizeLogisticsColumns(sheet);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String buildLogisticsRemark(LogisticsAllocationService.DeptAllocation dept) {
        double ratioPct = dept.ratio() * 100.0;
        return String.format(
                Locale.CHINA,
                "灭菌费基数 %.2f，占比 %.2f%%",
                dept.sterilizeTotal(),
                ratioPct);
    }

    private static void applyColumnWidths(XSSFSheet sheet) {
        sheet.setColumnWidth(0, (int) (1.69166666666667 * 256));
        sheet.setColumnWidth(1, (int) (25.875 * 256));
        sheet.setColumnWidth(2, (int) (17.2833333333333 * 256));
        sheet.setColumnWidth(3, (int) (32.425 * 256));
        sheet.setColumnWidth(4, (int) (18.85 * 256));
    }

    private static void autosizeLogisticsColumns(XSSFSheet sheet) {
        autosizeColumn(sheet, 0, 14);
        autosizeColumn(sheet, 1, 10);
        autosizeColumn(sheet, 2, 9.4);
        autosizeColumn(sheet, 3, 28);
    }

    private static void autosizeColumn(XSSFSheet sheet, int col, double minWidth) {
        sheet.autoSizeColumn(col);
        int width = sheet.getColumnWidth(col);
        int min = (int) (minWidth * 256);
        sheet.setColumnWidth(col, Math.min(Math.max(width, min), 256 * 48));
    }

    private static void setCell(XSSFRow row, int col, String value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private static void setAmountCell(XSSFRow row, int col, double value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(roundCurrency(value));
        cell.setCellStyle(style);
    }

    private static void setOptionalAmountCell(XSSFRow row, int col, Double value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(roundCurrency(value));
        }
        cell.setCellStyle(style);
    }

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class TableStyles {
        private final XSSFCellStyle titleStyle;
        private final XSSFCellStyle headerStyle;
        private final XSSFCellStyle bodyTextStyle;
        private final XSSFCellStyle bodyAmountStyle;
        private final XSSFCellStyle emptyStyle;
        private final XSSFCellStyle mergedRightStyle;

        private TableStyles(
                XSSFCellStyle titleStyle,
                XSSFCellStyle headerStyle,
                XSSFCellStyle bodyTextStyle,
                XSSFCellStyle bodyAmountStyle,
                XSSFCellStyle emptyStyle,
                XSSFCellStyle mergedRightStyle) {
            this.titleStyle = titleStyle;
            this.headerStyle = headerStyle;
            this.bodyTextStyle = bodyTextStyle;
            this.bodyAmountStyle = bodyAmountStyle;
            this.emptyStyle = emptyStyle;
            this.mergedRightStyle = mergedRightStyle;
        }

        static TableStyles create(XSSFWorkbook workbook) {
            DataFormat dataFormat = workbook.createDataFormat();
            short amountFormat = dataFormat.getFormat(AMOUNT_FORMAT);

            XSSFFont titleFont = createFont(workbook, TITLE_FONT_SIZE, true);
            XSSFFont headerFont = createFont(workbook, TITLE_FONT_SIZE, true);
            XSSFFont bodyFont = createFont(workbook, BODY_FONT_SIZE, false);

            XSSFCellStyle titleStyle = borderedStyle(workbook, titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle headerStyle = borderedStyle(workbook, headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle bodyTextStyle = borderedStyle(workbook, bodyFont);
            bodyTextStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle bodyAmountStyle = borderedStyle(workbook, bodyFont);
            bodyAmountStyle.setAlignment(HorizontalAlignment.CENTER);
            bodyAmountStyle.setDataFormat(amountFormat);

            XSSFCellStyle emptyStyle = baseStyle(workbook, bodyFont);

            XSSFCellStyle mergedRightStyle = borderedStyle(workbook, bodyFont);
            mergedRightStyle.setBorderLeft(BorderStyle.NONE);

            return new TableStyles(
                    titleStyle,
                    headerStyle,
                    bodyTextStyle,
                    bodyAmountStyle,
                    emptyStyle,
                    mergedRightStyle);
        }

        XSSFCellStyle titleStyle() {
            return titleStyle;
        }

        XSSFCellStyle headerStyle() {
            return headerStyle;
        }

        XSSFCellStyle bodyTextStyle() {
            return bodyTextStyle;
        }

        XSSFCellStyle bodyAmountStyle() {
            return bodyAmountStyle;
        }

        XSSFCellStyle emptyStyle() {
            return emptyStyle;
        }

        XSSFCellStyle mergedRightStyle() {
            return mergedRightStyle;
        }
    }

    private static XSSFFont createFont(XSSFWorkbook workbook, short size, boolean bold) {
        XSSFFont font = workbook.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints(size);
        font.setBold(bold);
        return font;
    }

    private static XSSFCellStyle baseStyle(XSSFWorkbook workbook, XSSFFont font) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        return style;
    }

    private static XSSFCellStyle borderedStyle(XSSFWorkbook workbook, XSSFFont font) {
        XSSFCellStyle style = baseStyle(workbook, font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
