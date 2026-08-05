package com.hospital.backend.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

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

    private ExcelBillImportSupport() {
    }

    static List<Map<String, Object>> parseWorkbook(InputStream inputStream) throws IOException {
        List<Map<String, Object>> allRows = new ArrayList<>();
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
        return allRows;
    }

    private static List<List<Object>> readSheetMatrix(Sheet sheet) {
        List<List<Object>> matrix = new ArrayList<>();
        for (Row row : sheet) {
            List<Object> rowData = new ArrayList<>();
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
            Set<String> norm = normalizedCells(matrix.get(r));
            if (norm.contains("发货日期") && norm.contains("包名")
                    && norm.contains("包装材料") && norm.contains("器械数")
                    && norm.contains("单价") && norm.contains("总价")) {
                return r;
            }
        }
        for (int r = 0; r < matrix.size(); r++) {
            Set<String> norm = normalizedCells(matrix.get(r));
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
