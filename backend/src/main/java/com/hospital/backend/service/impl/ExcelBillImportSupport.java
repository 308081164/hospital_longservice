package com.hospital.backend.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 账单导入解析：兼容铂康原始表（含包装材料/器械数）与系统导出 8 列表（含合并单 sheet + 行内科室）。
 */
final class ExcelBillImportSupport {

    private static final java.util.regex.Pattern DATE_RANGE_TEXT = java.util.regex.Pattern.compile(
            "^(从|时间|日期)[：:]?\\s*\\d{4}.*(?:至|到).*\\d{4}.*"
                    + "|^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*(?:至|到).*\\d{4}.*");
    /** 铂康标准账单医院名固定列：D 列（0-based index 3）。 */
    private static final int STANDARD_HOSPITAL_NAME_COLUMN = 3;
    /** 合并汇总表：医院全称常出现在首列（发货日期列）汇总行。 */
    private static final int SUMMARY_HOSPITAL_NAME_COLUMN = 0;

    private ExcelBillImportSupport() {
    }

    record WorkbookParseResult(
            List<Map<String, Object>> rows,
            List<String> hospitalDisplayNames,
            List<String> headerAreaTexts) {
    }

    static List<Map<String, Object>> parseWorkbook(InputStream inputStream) throws IOException {
        return parseWorkbookData(inputStream).rows();
    }

    static List<String> extractHospitalDisplayNames(byte[] fileBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            return parseWorkbookData(in).hospitalDisplayNames();
        }
    }

    /** 表头区域（首个表头行及其上方元数据）内所有非空单元格文本，供客户别名解析医院全称。 */
    static List<String> extractHeaderAreaTexts(byte[] fileBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            return parseWorkbookData(in).headerAreaTexts();
        }
    }

    static WorkbookParseResult parseWorkbookData(InputStream inputStream) throws IOException {
        List<Map<String, Object>> allRows = new ArrayList<>();
        LinkedHashSet<String> hospitalDisplayNames = new LinkedHashSet<>();
        LinkedHashSet<String> headerAreaTexts = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                List<List<Object>> matrix = readSheetMatrix(sheet);
                if (matrix.isEmpty()) {
                    continue;
                }
                int headerIdx = findHeaderRowIndex(matrix);
                if (headerIdx < 0) {
                    continue;
                }
                collectHospitalDisplayNames(matrix, headerIdx, hospitalDisplayNames);
                collectHeaderAreaTexts(matrix, headerIdx, headerAreaTexts);
                Map<String, Integer> headerMap = buildHeaderMap(matrix.get(headerIdx));
                boolean combinedSheet = isCombinedImportSheet(sheetName, headerMap);
                String currentDept = sheetName;
                for (int r = headerIdx + 1; r < matrix.size(); r++) {
                    List<Object> row = matrix.get(r);
                    Object deliveryDateRaw = getCellByHeader(row, headerMap, "发货日期");
                    String deliveryText = sanitizeStr(deliveryDateRaw);
                    String orderNo = sanitizeStr(getCellByHeader(row, headerMap, "发货单号"));
                    String type = firstNonBlank(
                            sanitizeStr(getCellByHeader(row, headerMap, "类型")),
                            sanitizeStr(getCellByHeader(row, headerMap, "基本类型")));
                    String categoryNo = sanitizeStr(getCellByHeader(row, headerMap, "包类别号"));
                    String packName = sanitizeStr(getCellByHeader(row, headerMap, "包名"));
                    String packageMaterial = sanitizeStr(getCellByHeader(row, headerMap, "包装材料"));
                    double packCount = toDoubleVal(getCellByHeader(row, headerMap, "包数"));
                    double instrumentCount = toDoubleVal(getCellByHeader(row, headerMap, "器械数"));
                    Object unitPriceRaw = getCellByHeader(row, headerMap, "单价");
                    Object totalPriceRaw = getCellByHeader(row, headerMap, "总价");

                    if (isHospitalSummaryRow(deliveryText)) {
                        continue;
                    }
                    if (combinedSheet && isInlineDepartmentMarkerRow(deliveryText, orderNo, type, packName)) {
                        currentDept = deliveryText.trim();
                        continue;
                    }

                    boolean hasDate = hasDeliveryDate(deliveryDateRaw);
                    boolean hasKeyFields = !type.isEmpty() && !packName.isEmpty();
                    if (!hasDate || !hasKeyFields) {
                        continue;
                    }

                    Map<String, Object> rowData = new LinkedHashMap<>();
                    rowData.put("sheetName", combinedSheet ? currentDept : sheetName);
                    rowData.put("rowNumber", r + 1);
                    rowData.put("deliveryDate", formatExcelDate(deliveryDateRaw));
                    rowData.put("orderNo", orderNo);
                    rowData.put("type", type);
                    rowData.put("categoryNo", categoryNo);
                    rowData.put("packName", packName);
                    rowData.put("packageMaterial", packageMaterial);
                    rowData.put("packCount", (int) packCount);
                    rowData.put("instrumentCount", (int) instrumentCount);
                    rowData.put("unitPrice", toDoubleOrNull(unitPriceRaw));
                    rowData.put("totalPrice", toDoubleOrNull(totalPriceRaw));
                    allRows.add(rowData);
                }
            }
        }
        return new WorkbookParseResult(allRows, List.copyOf(hospitalDisplayNames), List.copyOf(headerAreaTexts));
    }

    private static void collectHeaderAreaTexts(
            List<List<Object>> matrix, int headerRowIndex, Set<String> out) {
        // 仅扫描表头行及其上方元数据，避免明细行包名污染别名解析。
        for (int r = 0; r <= headerRowIndex && r < matrix.size(); r++) {
            for (Object cell : matrix.get(r)) {
                String text = sanitizeStr(cell);
                if (!text.isBlank()) {
                    out.add(text.trim());
                }
            }
        }
    }

    private static void collectHospitalDisplayNames(
            List<List<Object>> matrix, int headerRowIndex, Set<String> out) {
        if (!out.isEmpty()) {
            return;
        }
        String fromD = resolveHospitalNameFromColumnD(matrix, headerRowIndex);
        if (!fromD.isBlank()) {
            out.add(fromD);
            return;
        }
        String fromSummaryColA = resolveHospitalNameFromColumnASummaryRows(matrix, headerRowIndex);
        if (!fromSummaryColA.isBlank()) {
            out.add(fromSummaryColA);
        }
    }

    static boolean isLikelyHospitalDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        if (!trimmed.contains("医院")) {
            return false;
        }
        if (trimmed.contains("发货单汇总表") || isDateRangeText(trimmed)) {
            return false;
        }
        if (isInlineDepartmentMarkerRow(trimmed, "", "", "")) {
            return false;
        }
        if (trimmed.contains("医院") && trimmed.contains("至")) {
            return false;
        }
        if (trimmed.matches("^[\\d.]+$")) {
            return false;
        }
        if ("发货日期".equals(trimmed)) {
            return false;
        }
        return true;
    }

    static boolean isDateRangeText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return DATE_RANGE_TEXT.matcher(text.trim()).find();
    }

    static String extractDateRangeFromHeaderTexts(List<String> headerAreaTexts) {
        if (headerAreaTexts == null || headerAreaTexts.isEmpty()) {
            return "";
        }
        for (String text : headerAreaTexts) {
            if (isDateRangeText(text)) {
                return text.trim();
            }
        }
        return "";
    }

    /** 铂康标准账单：医院全称仅在 D8 或 D9（二选一），D9 优先。 */
    static String resolveHospitalNameFromColumnD(List<List<Object>> matrix, int headerRowIndex) {
        String d9 = readColumnDCell(matrix, 8);
        if (isLikelyHospitalDisplayName(d9)) {
            return d9.trim();
        }
        String d8 = readColumnDCell(matrix, 7);
        if (isLikelyHospitalDisplayName(d8)) {
            return d8.trim();
        }
        return "";
    }

    /**
     * 合并单 sheet 账单（如红十字处理后表）：医院名在表头下一行 A 列汇总行，D8/D9 为空。
     */
    static String resolveHospitalNameFromColumnASummaryRows(List<List<Object>> matrix, int headerRowIndex) {
        for (int r = headerRowIndex + 1; r < matrix.size(); r++) {
            List<Object> row = matrix.get(r);
            if (row.isEmpty()) {
                continue;
            }
            String colA = sanitizeStr(row.size() > SUMMARY_HOSPITAL_NAME_COLUMN
                    ? row.get(SUMMARY_HOSPITAL_NAME_COLUMN) : null);
            if (!isHospitalSummaryRow(colA) || !isLikelyHospitalDisplayName(colA)) {
                continue;
            }
            return colA.trim();
        }
        return "";
    }

    private static String readColumnDCell(List<List<Object>> matrix, int rowIndex) {
        if (rowIndex < 0 || rowIndex >= matrix.size()) {
            return "";
        }
        List<Object> row = matrix.get(rowIndex);
        if (row.size() <= STANDARD_HOSPITAL_NAME_COLUMN) {
            return "";
        }
        return sanitizeStr(row.get(STANDARD_HOSPITAL_NAME_COLUMN));
    }

    private static List<List<Object>> readSheetMatrix(Sheet sheet) {
        List<List<Object>> matrix = new ArrayList<>();
        // 按 Excel 行号（0-based）对齐，D8/D9 与表头行索引才能与铂康模板一致
        int lastRow = sheet.getLastRowNum();
        if (lastRow < 0) {
            return matrix;
        }
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            List<Object> rowData = new ArrayList<>();
            if (row == null) {
                matrix.add(rowData);
                continue;
            }
            short lastCell = row.getLastCellNum();
            if (lastCell < 0) {
                matrix.add(rowData);
                continue;
            }
            for (int c = 0; c < lastCell; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    rowData.add("");
                    continue;
                }
                switch (cell.getCellType()) {
                    case NUMERIC -> {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            rowData.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                        } else {
                            double v = cell.getNumericCellValue();
                            rowData.add(v == Math.floor(v) && !Double.isInfinite(v) ? (long) v : v);
                        }
                    }
                    case FORMULA -> {
                        // 公式单元格读取 Excel 保存时缓存的计算结果（即 Excel 中显示的数值），
                        // 否则器械数等列会被当作空串抹掉
                        switch (cell.getCachedFormulaResultType()) {
                            case NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    rowData.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                                } else {
                                    double v = cell.getNumericCellValue();
                                    rowData.add(v == Math.floor(v) && !Double.isInfinite(v) ? (long) v : v);
                                }
                            }
                            case STRING -> rowData.add(cell.getStringCellValue());
                            case BOOLEAN -> rowData.add(cell.getBooleanCellValue());
                            default -> rowData.add("");
                        }
                    }
                    case STRING -> rowData.add(cell.getStringCellValue());
                    case BOOLEAN -> rowData.add(cell.getBooleanCellValue());
                    default -> rowData.add("");
                }
            }
            matrix.add(rowData);
        }
        return matrix;
    }

    static int findHeaderRowIndex(List<List<Object>> matrix) {
        for (int r = 0; r < matrix.size(); r++) {
            Set<String> norm = normalizedHeaderCells(matrix.get(r));
            if (norm.contains("发货日期") && norm.contains("包名")
                    && norm.contains("包装材料") && norm.contains("器械数")
                    && norm.contains("单价") && norm.contains("总价")) {
                return r;
            }
        }
        for (int r = 0; r < matrix.size(); r++) {
            Set<String> norm = normalizedHeaderCells(matrix.get(r));
            if (norm.contains("发货日期") && norm.contains("包名")
                    && norm.contains("单价") && norm.contains("总价")) {
                return r;
            }
        }
        return -1;
    }

    static boolean isCombinedImportSheet(String sheetName, Map<String, Integer> headerMap) {
        String normalizedSheet = normalizeCellText(sheetName);
        if ("账单".equals(normalizedSheet) || "合计".equals(normalizedSheet)
                || normalizedSheet.contains("汇总")) {
            return true;
        }
        return headerMap.containsKey(normalizeCellText("基本类型"));
    }

    static boolean isHospitalSummaryRow(String deliveryText) {
        if (deliveryText == null || deliveryText.isBlank()) {
            return false;
        }
        return deliveryText.contains("医院") || deliveryText.contains("中心");
    }

    static boolean isInlineDepartmentMarkerRow(String deliveryText, String orderNo, String type, String packName) {
        if (deliveryText == null || deliveryText.isBlank() || hasDeliveryDate(deliveryText)) {
            return false;
        }
        if (isHospitalSummaryRow(deliveryText)) {
            return false;
        }
        if (!orderNo.isEmpty() || !type.isEmpty() || !packName.isEmpty()) {
            return false;
        }
        return true;
    }

    private static Map<String, Integer> buildHeaderMap(List<Object> headerRow) {
        Map<String, Integer> headerMap = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.size(); c++) {
            registerHeaderAlias(headerMap, normalizeCellText(headerRow.get(c)), c);
        }
        aliasHeader(headerMap, "灭菌日期", "发货日期");
        aliasHeader(headerMap, "器械名称", "包名");
        aliasHeader(headerMap, "单包内器械数量/把", "器械数");
        aliasHeader(headerMap, "器械数量", "器械数");
        aliasHeader(headerMap, "灭菌锅次", "发货单号");
        aliasHeader(headerMap, "病人ID", "包类别号");
        return headerMap;
    }

    private static void registerHeaderAlias(Map<String, Integer> headerMap, String key, int columnIndex) {
        if (key == null || key.isEmpty() || headerMap.containsKey(key)) {
            return;
        }
        headerMap.put(key, columnIndex);
    }

    private static void aliasHeader(Map<String, Integer> headerMap, String alias, String canonical) {
        Integer idx = headerMap.get(normalizeCellText(alias));
        if (idx != null && !headerMap.containsKey(canonical)) {
            headerMap.put(canonical, idx);
        }
    }

    private static Set<String> normalizedCells(List<Object> row) {
        Set<String> norm = new LinkedHashSet<>();
        for (Object cell : row) {
            String text = normalizeCellText(cell);
            if (!text.isEmpty()) {
                norm.add(text);
            }
        }
        return norm;
    }

    /** 表头行归一化：兼容「器械\\n数量」「器械数量」等等价于「器械数」。 */
    private static Set<String> normalizedHeaderCells(List<Object> row) {
        Set<String> norm = normalizedCells(row);
        if (norm.contains("器械数量")) {
            norm.add("器械数");
        }
        return norm;
    }

    private static boolean hasDeliveryDate(Object deliveryDateRaw) {
        if (deliveryDateRaw instanceof Number number) {
            return number.doubleValue() > 40000;
        }
        return String.valueOf(deliveryDateRaw).matches(".*\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static String normalizeCellText(Object value) {
        return String.valueOf(value).replaceAll("\\s+", "").trim();
    }

    private static Object getCellByHeader(List<Object> row, Map<String, Integer> headerMap, String headerName) {
        Integer idx = headerMap.get(normalizeCellText(headerName));
        if (idx == null || idx >= row.size()) {
            return null;
        }
        return row.get(idx);
    }

    private static String sanitizeStr(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isInfinite(d) || Double.isNaN(d)) {
                return "";
            }
            if (d == Math.floor(d)) {
                return String.valueOf((long) d);
            }
            return new DecimalFormat("#.##########").format(d);
        }
        return String.valueOf(value).trim();
    }

    private static double toDoubleVal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.replace(",", "").trim());
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Double toDoubleOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                String s = text.replace(",", "").replace("￥", "").trim();
                if (s.isEmpty()) {
                    return null;
                }
                return Double.parseDouble(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String formatExcelDate(Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d > 40000 && d < 60000) {
                java.util.Date date = DateUtil.getJavaDate(d);
                if (date != null) {
                    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
                }
            }
        }
        return String.valueOf(value != null ? value : "").trim();
    }
}
