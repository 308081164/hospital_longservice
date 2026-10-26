package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.BillingMonthResolver;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * 把 10.20 样式表画成工作簿。标准结款函模板仍给呼兰、九洲、仁胜等已验收医院。
 */
public final class ClerkSheetStyleWorkbook {

    private ClerkSheetStyleWorkbook() {
    }

    public static byte[] mergedLetter(
            HospitalReconciliationJob job,
            JsonNode compiled,
            double sanfuSterilize,
            double xiangfangSterilize,
            double logisticsAmount) throws Exception {
        List<ClerkSheetStyle.LetterLine> lines = ClerkSheetStyle.mergedLines(
                compiled, sanfuSterilize, xiangfangSterilize, logisticsAmount);
        double total = ClerkSheetStyle.letterTotal(lines);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("结款函");
            Styles styles = Styles.create(workbook);
            int row = 0;
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, ClerkSheetStyle.mergedTitle(job), styles.title);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, "致：" + ClerkSheetStyle.MERGED_TITLE_HOSPITALS, styles.body);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, intro(job), styles.body);
            header(sheet.createRow(row++), styles.header, "序号", "条目", "费用", "备注");
            for (ClerkSheetStyle.LetterLine line : lines) {
                Row data = sheet.createRow(row++);
                text(data, 0, line.index(), styles.body);
                text(data, 1, line.item(), styles.body);
                amount(data, 2, line.amount(), styles.amount);
                text(data, 3, line.remark(), styles.body);
            }
            Row totalRow = sheet.createRow(row++);
            text(totalRow, 0, "合计", styles.header);
            text(totalRow, 1, "", styles.header);
            amount(totalRow, 2, total, styles.amount);
            text(totalRow, 3, "", styles.body);
            Row upper = sheet.createRow(row);
            text(upper, 0, "合计大写", styles.header);
            merge(sheet, row, 1, 3);
            text(upper, 1, ChineseAmountFormatter.format(total), styles.body);
            widths(sheet, 12, 16, 14, 28);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] guoyaoLetter(
            HospitalReconciliationJob job,
            JsonNode compiled,
            List<HospitalReconciliationRow> rows) throws Exception {
        List<ClerkSheetStyle.GuoyaoLine> lines = ClerkSheetStyle.guoyaoLines(compiled, rows);
        double sterilize = ClerkSheetStyle.guoyaoSterilizeTotal(lines);
        String hospital = job != null && job.getHospitalName() != null ? job.getHospitalName() : "国药总医院";
        Integer month = ClerkSheetStyle.monthOf(job);
        String title = month == null ? hospital + "结款通知函" : hospital + month + "月份结款通知函";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("结款函");
            Styles styles = Styles.create(workbook);
            int row = 0;
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, title, styles.title);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, "致：" + hospital, styles.body);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, intro(job), styles.body);
            header(sheet.createRow(row++), styles.header, "器械包类型", "器械把数量", "核算数量", "计费金额");
            for (ClerkSheetStyle.GuoyaoLine line : lines) {
                Row data = sheet.createRow(row++);
                text(data, 0, line.category(), styles.body);
                if (line.handles() == null) {
                    text(data, 1, "", styles.body);
                } else {
                    amount(data, 1, line.handles().doubleValue(), styles.amount);
                }
                amount(data, 2, line.accounted() == null ? 0 : line.accounted().doubleValue(), styles.amount);
                amount(data, 3, line.billedAmount(), styles.amount);
            }
            Row logistics = sheet.createRow(row++);
            logistics.setHeightInPoints(36);
            text(logistics, 0, "物流", styles.body);
            merge(sheet, logistics.getRowNum(), 1, 3);
            text(logistics, 1, logisticsText(job), styles.wrap);
            Row sterilizeRow = sheet.createRow(row++);
            text(sterilizeRow, 0, "灭菌费", styles.body);
            merge(sheet, sterilizeRow.getRowNum(), 1, 3);
            amount(sterilizeRow, 1, sterilize, styles.amount);
            Row upper = sheet.createRow(row);
            text(upper, 0, "大写", styles.body);
            merge(sheet, row, 1, 3);
            text(upper, 1, ChineseAmountFormatter.format(sterilize), styles.body);
            widths(sheet, 22, 16, 14, 18);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] discountColumnLetter(
            HospitalReconciliationJob job,
            JsonNode compiled,
            double sterilizeOriginal,
            double externalOriginal,
            double logisticsAmount) throws Exception {
        List<ClerkSheetStyle.LetterLine> lines = ClerkSheetStyle.discountColumnLines(
                compiled, sterilizeOriginal, externalOriginal, logisticsAmount);
        String discountedHeader = "7折后（元）";
        for (ClerkSheetStyle.LetterLine line : lines) {
            if ("header".equals(line.index())) {
                discountedHeader = line.item();
            }
        }
        double total = ClerkSheetStyle.discountColumnTotal(lines);
        String hospital = job != null && job.getHospitalName() != null ? job.getHospitalName() : "";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("结款函");
            Styles styles = Styles.create(workbook);
            int row = 0;
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, "黑龙江中医药大学附属第三医院 结款通知函", styles.title);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, "致：黑龙江中医药大学附属第三医院", styles.body);
            merge(sheet, row, 0, 3);
            text(sheet.createRow(row++), 0, intro(job), styles.body);
            header(sheet.createRow(row++), styles.header, "序号", "条目", "原价（元）", discountedHeader);
            for (ClerkSheetStyle.LetterLine line : lines) {
                if ("header".equals(line.index())) {
                    continue;
                }
                Row data = sheet.createRow(row++);
                text(data, 0, line.index(), styles.body);
                text(data, 1, line.item(), styles.body);
                if ("物流费用".equals(line.item())) {
                    merge(sheet, data.getRowNum(), 2, 3);
                    amount(data, 2, line.amount(), styles.amount);
                } else {
                    amount(data, 2, line.amount(), styles.amount);
                    amount(data, 3, line.discountedAmount(), styles.amount);
                }
            }
            Row totalRow = sheet.createRow(row++);
            text(totalRow, 0, "合计", styles.header);
            text(totalRow, 1, "", styles.header);
            merge(sheet, totalRow.getRowNum(), 2, 3);
            amount(totalRow, 2, total, styles.amount);
            Row upper = sheet.createRow(row);
            text(upper, 0, "合计大写", styles.header);
            merge(sheet, row, 1, 3);
            text(upper, 1, ChineseAmountFormatter.format(total), styles.body);
            if (hospital != null) {
                sheet.getRow(0).setHeightInPoints(24);
            }
            widths(sheet, 12, 16, 16, 16);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] zuyanDeptPrice(HospitalReconciliationJob job, List<HospitalReconciliationRow> rows)
            throws Exception {
        return zuyanDeptPrice(job, rows, job != null && job.getLogisticsFee() != null ? job.getLogisticsFee() : 0);
    }

    public static byte[] zuyanDeptPrice(
            HospitalReconciliationJob job,
            List<HospitalReconciliationRow> rows,
            double logistics) throws Exception {
        List<ClerkSheetStyle.DeptPriceLine> lines = ClerkSheetStyle.zuyanDeptLines(rows, logistics);
        double baseTotal = 0;
        double logisticsTotal = 0;
        double grand = 0;
        for (ClerkSheetStyle.DeptPriceLine line : lines) {
            baseTotal += line.baseAmount();
            logisticsTotal += line.logisticsAmount();
            grand += line.totalAmount();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("科室灭菌价格汇总");
            Styles styles = Styles.create(workbook);
            merge(sheet, 0, 0, 4);
            text(sheet.createRow(0), 0, ClerkSheetStyle.zuyanTitle(job), styles.title);
            Row headerTop = sheet.createRow(1);
            Row headerBottom = sheet.createRow(2);
            text(headerTop, 0, "科室", styles.header);
            text(headerTop, 1, "基础器械", styles.header);
            text(headerTop, 2, "物流费用", styles.header);
            text(headerTop, 3, "价格", styles.header);
            text(headerTop, 4, "确认签字", styles.header);
            text(headerBottom, 3, "合计费用", styles.header);
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 2, 2));
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 4, 4));
            int row = 3;
            for (ClerkSheetStyle.DeptPriceLine line : lines) {
                Row data = sheet.createRow(row++);
                text(data, 0, line.department(), styles.body);
                amount(data, 1, line.baseAmount(), styles.amount);
                amount(data, 2, line.logisticsAmount(), styles.amount);
                amount(data, 3, line.totalAmount(), styles.amount);
                text(data, 4, "", styles.body);
            }
            Row total = sheet.createRow(row);
            text(total, 0, "合计", styles.header);
            amount(total, 1, ClerkSheetStyle.round2(baseTotal), styles.amount);
            amount(total, 2, ClerkSheetStyle.round2(logisticsTotal), styles.amount);
            amount(total, 3, ClerkSheetStyle.round2(grand), styles.amount);
            text(total, 4, "", styles.body);
            widths(sheet, 18, 14, 14, 14, 14);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] zy3DeptPieces(HospitalReconciliationJob job, List<HospitalReconciliationRow> rows)
            throws Exception {
        Map<String, Integer> byDept = new java.util.LinkedHashMap<>();
        if (rows != null) {
            for (HospitalReconciliationRow row : rows) {
                if (row == null || "skipped".equalsIgnoreCase(row.getStatus())) {
                    continue;
                }
                String dept = row.getSheetName() == null || row.getSheetName().isBlank()
                        ? "(默认)"
                        : row.getSheetName().trim();
                int pieces = row.getInstrumentCount() != null ? row.getInstrumentCount() : 0;
                byDept.merge(dept, pieces, Integer::sum);
            }
        }
        String hospital = job != null && job.getHospitalName() != null ? job.getHospitalName() : "医院";
        String title = hospital + piecePeriod(job) + "分科室处理器械把数汇总";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("分科室把数汇总");
            Styles styles = Styles.create(workbook);
            merge(sheet, 0, 0, 3);
            text(sheet.createRow(0), 0, title, styles.title);
            header(sheet.createRow(1), styles.header, "序号", "科室", "器械数量", "科室签字");
            int row = 2;
            int seq = 1;
            int total = 0;
            for (Map.Entry<String, Integer> entry : byDept.entrySet()) {
                Row data = sheet.createRow(row++);
                text(data, 0, String.valueOf(seq++), styles.body);
                text(data, 1, entry.getKey(), styles.body);
                amount(data, 2, entry.getValue(), styles.amount);
                text(data, 3, "", styles.body);
                total += entry.getValue();
            }
            Row totalRow = sheet.createRow(row);
            text(totalRow, 0, "", styles.header);
            text(totalRow, 1, "合计", styles.header);
            amount(totalRow, 2, total, styles.amount);
            text(totalRow, 3, "", styles.body);
            widths(sheet, 10, 28, 14, 14);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static String logisticsText(HospitalReconciliationJob job) {
        double payable = job != null && job.getLogisticsFee() != null ? job.getLogisticsFee() : 0;
        Map<String, Object> breakdown = parseBreakdown(job);
        double deducted = number(breakdown, "cardDeducted");
        double before = number(breakdown, "cardBalanceBefore");
        double after = number(breakdown, "cardBalanceAfter");
        double receivable = payable + deducted;
        String amountLine = ClerkSheetStyle.formatUnit(payable) + "元";
        if (before > 0 || deducted > 0 || after > 0) {
            return amountLine + "\n（本月应收物流费" + ClerkSheetStyle.formatUnit(receivable)
                    + "元，实收" + ClerkSheetStyle.formatUnit(payable)
                    + "元，" + ClerkSheetStyle.formatUnit(before) + "元物流卡余额"
                    + ClerkSheetStyle.formatUnit(after) + "元）";
        }
        Object feePerTrip = breakdown == null ? null : breakdown.get("feePerTrip");
        if (feePerTrip instanceof Number fee) {
            return amountLine + "\n（" + ClerkSheetStyle.formatUnit(fee.doubleValue()) + "/次）";
        }
        return amountLine;
    }

    private static String intro(HospitalReconciliationJob job) {
        return SettlementPeriodResolver.resolve(job)
                .map(SettlementPeriodFormatter::formatSettlementIntro)
                .orElse("灭菌费用总清单如下：");
    }

    private static String piecePeriod(HospitalReconciliationJob job) {
        String month = BillingMonthResolver.resolve(job);
        if (month != null && month.length() >= 7) {
            return Integer.parseInt(month.substring(0, 4)) + "年"
                    + Integer.parseInt(month.substring(5, 7)) + "月";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBreakdown(HospitalReconciliationJob job) {
        if (job == null || job.getLogisticsBreakdown() == null || job.getLogisticsBreakdown().isBlank()) {
            return null;
        }
        return JsonUtils.parseToMap(job.getLogisticsBreakdown());
    }

    private static double number(Map<String, Object> breakdown, String key) {
        if (breakdown == null || !(breakdown.get(key) instanceof Number number)) {
            return 0;
        }
        return number.doubleValue();
    }

    private static void header(Row row, CellStyle style, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            text(row, i, labels[i], style);
        }
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void amount(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(ClerkSheetStyle.round2(value));
        cell.setCellStyle(style);
    }

    private static void merge(XSSFSheet sheet, int row, int from, int to) {
        sheet.addMergedRegion(new CellRangeAddress(row, row, from, to));
    }

    private static void widths(XSSFSheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle amount;
        private final CellStyle wrap;

        private Styles(CellStyle title, CellStyle header, CellStyle body, CellStyle amount, CellStyle wrap) {
            this.title = title;
            this.header = header;
            this.body = body;
            this.amount = amount;
            this.wrap = wrap;
        }

        private static Styles create(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle title = border(workbook);
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle header = border(workbook);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle body = border(workbook);
            body.setAlignment(HorizontalAlignment.CENTER);
            CellStyle amount = border(workbook);
            amount.setAlignment(HorizontalAlignment.RIGHT);
            amount.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            CellStyle wrap = border(workbook);
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);
            return new Styles(title, header, body, amount, wrap);
        }

        private static CellStyle border(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }
}
