package com.hospital.backend.export.fuyi;

import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.BillExportPriceResolver;
import com.hospital.backend.service.LogisticsAllocationService;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
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
 * 中医附一（ZYY-D1）分科室汇总 / 物流分摊附表：对齐客户确认包样表版式。
 *
 * <p>样例：{@code docs/账单导出客户确认包/样例-按导出类型/03-十一列账单（附一）/}
 */
public final class FuyiSupplementWorkbookBuilder {

    public static final String DEPT_SUMMARY_SHEET = "分科室汇总";
    public static final String LOGISTICS_SHEET = "物流分摊";

    private static final double COL_A_WIDTH = 21.38671875;
    private static final double COL_B_WIDTH = 9.39453125;
    private static final short FONT_SIZE = 11;

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
            deptSums.merge(sheet, resolveExportRowTotal(row), Double::sum);
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

    public static byte[] buildDeptSummaryWorkbook(String hospitalName, List<HospitalReconciliationRow> rows)
            throws IOException {
        List<Map.Entry<String, Double>> sorted = sortDeptTotals(aggregateDeptTotals(rows));
        double grandTotal = sorted.stream().mapToDouble(Map.Entry::getValue).sum();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(DEPT_SUMMARY_SHEET);
            applyDeptSummaryColumnWidths(sheet);

            XSSFFont normalFont = createFont(workbook, false);
            XSSFFont boldFont = createFont(workbook, true);
            XSSFCellStyle normalStyle = createTextStyle(workbook, normalFont);
            XSSFCellStyle boldStyle = createTextStyle(workbook, boldFont);

            int rowIdx = 0;
            XSSFRow titleRow = sheet.createRow(rowIdx++);
            setCell(titleRow, 0, title(hospitalName), normalStyle);

            XSSFRow headerRow = sheet.createRow(rowIdx++);
            setCell(headerRow, 0, "科室", boldStyle);
            setCell(headerRow, 1, "金额", boldStyle);

            for (Map.Entry<String, Double> entry : sorted) {
                XSSFRow dataRow = sheet.createRow(rowIdx++);
                setCell(dataRow, 0, entry.getKey(), normalStyle);
                setAmountCell(dataRow, 1, entry.getValue(), normalStyle);
            }

            XSSFRow totalRow = sheet.createRow(rowIdx);
            setCell(totalRow, 0, "合计", normalStyle);
            setAmountCell(totalRow, 1, grandTotal, normalStyle);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] buildLogisticsAllocationWorkbook(
            List<LogisticsAllocationService.DeptAllocation> departments) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(LOGISTICS_SHEET);

            XSSFFont normalFont = createFont(workbook, false);
            XSSFFont boldFont = createFont(workbook, true);
            XSSFCellStyle normalStyle = createTextStyle(workbook, normalFont);
            XSSFCellStyle boldStyle = createTextStyle(workbook, boldFont);

            XSSFRow headerRow = sheet.createRow(0);
            setCell(headerRow, 0, "科室", boldStyle);
            setCell(headerRow, 1, "类型", boldStyle);
            setCell(headerRow, 2, "金额", boldStyle);
            setCell(headerRow, 3, "备注", boldStyle);

            int rowIdx = 1;
            if (departments != null) {
                List<LogisticsAllocationService.DeptAllocation> sorted = new ArrayList<>(departments);
                Collator collator = Collator.getInstance(Locale.CHINA);
                sorted.sort((a, b) -> collator.compare(a.department(), b.department()));
                for (LogisticsAllocationService.DeptAllocation dept : sorted) {
                    if (Math.abs(dept.allocatedFee()) < 0.005) {
                        continue;
                    }
                    XSSFRow row = sheet.createRow(rowIdx++);
                    setCell(row, 0, dept.department(), normalStyle);
                    setCell(row, 1, "物流分摊", normalStyle);
                    setAmountCell(row, 2, dept.allocatedFee(), normalStyle);
                    setCell(row, 3, buildLogisticsRemark(dept), normalStyle);
                }
            }

            autosizeLogisticsColumns(sheet);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String title(String hospitalName) {
        String name = hospitalName != null && !hospitalName.isBlank() ? hospitalName : "医院";
        return name + " — 分科室汇总";
    }

    private static String buildLogisticsRemark(LogisticsAllocationService.DeptAllocation dept) {
        double ratioPct = dept.ratio() * 100.0;
        return String.format(
                Locale.CHINA,
                "灭菌费基数 %.2f，占比 %.2f%%",
                dept.sterilizeTotal(),
                ratioPct);
    }

    private static void applyDeptSummaryColumnWidths(XSSFSheet sheet) {
        sheet.setColumnWidth(0, (int) (COL_A_WIDTH * 256));
        sheet.setColumnWidth(1, (int) (COL_B_WIDTH * 256));
    }

    private static void autosizeLogisticsColumns(XSSFSheet sheet) {
        for (int col = 0; col < 4; col++) {
            sheet.autoSizeColumn(col);
            int width = sheet.getColumnWidth(col);
            sheet.setColumnWidth(col, Math.min(Math.max(width, 256 * 8), 256 * 40));
        }
    }

    private static XSSFFont createFont(XSSFWorkbook workbook, boolean bold) {
        XSSFFont font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints(FONT_SIZE);
        font.setBold(bold);
        return font;
    }

    private static XSSFCellStyle createTextStyle(XSSFWorkbook workbook, XSSFFont font) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.GENERAL);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
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

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
