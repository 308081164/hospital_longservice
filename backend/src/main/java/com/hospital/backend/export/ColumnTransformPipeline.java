package com.hospital.backend.export;

import com.hospital.backend.export.model.ColumnMappingConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies column delete / insert / rename transforms on exported workbooks.
 * FR-M3-21 foundation — full hospital-specific rules live in export_template.column_mapping.
 */
@Slf4j
@Component
public class ColumnTransformPipeline {

    public byte[] apply(byte[] workbookBytes, ColumnMappingConfig config) {
        if (workbookBytes == null || workbookBytes.length == 0 || config == null) {
            return workbookBytes;
        }
        boolean hasWork = !config.getRemoveColumns().isEmpty()
                || !config.getInsertColumns().isEmpty()
                || !config.getRenameColumns().isEmpty()
                || !config.getKeepColumns().isEmpty();
        if (!hasWork) {
            return workbookBytes;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(workbookBytes);
             XSSFWorkbook workbook = new XSSFWorkbook(bis)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                transformSheet(sheet, config);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("ColumnTransformPipeline failed, returning original bytes: {}", e.getMessage());
            return workbookBytes;
        }
    }

    void transformSheet(Sheet sheet, ColumnMappingConfig config) {
        int headerRowIdx = findHeaderRowIndex(sheet);
        if (headerRowIdx < 0) {
            return;
        }
        Row headerRow = sheet.getRow(headerRowIdx);
        if (headerRow == null) {
            return;
        }

        Map<String, Integer> headerToCol = readHeaderMap(headerRow);
        applyRenames(headerRow, headerToCol, config.getRenameColumns());

        List<Integer> colsToRemove = resolveColumnsToRemove(headerToCol, config);
        if (colsToRemove.isEmpty() && config.getInsertColumns().isEmpty()) {
            return;
        }

        colsToRemove.sort((a, b) -> b - a);
        for (int colIdx : colsToRemove) {
            removeColumn(sheet, colIdx, headerRowIdx);
        }
    }

    private void applyRenames(Row headerRow, Map<String, Integer> headerToCol, Map<String, String> renameColumns) {
        if (renameColumns == null || renameColumns.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : renameColumns.entrySet()) {
            Integer col = headerToCol.get(normalizeHeader(entry.getKey()));
            if (col != null) {
                Cell cell = headerRow.getCell(col);
                if (cell != null) {
                    cell.setCellValue(entry.getValue());
                }
            }
        }
    }

    List<Integer> resolveColumnsToRemove(Map<String, Integer> headerToCol, ColumnMappingConfig config) {
        List<Integer> result = new ArrayList<>();
        if (!config.getKeepColumns().isEmpty()) {
            Set<String> keepNormalized = config.getKeepColumns().stream()
                    .map(this::normalizeHeader)
                    .collect(Collectors.toSet());
            for (Map.Entry<String, Integer> entry : headerToCol.entrySet()) {
                if (!keepNormalized.contains(entry.getKey())) {
                    result.add(entry.getValue());
                }
            }
            return result;
        }
        for (String remove : config.getRemoveColumns()) {
            Integer col = headerToCol.get(normalizeHeader(remove));
            if (col != null) {
                result.add(col);
            }
        }
        return result;
    }

    private Map<String, Integer> readHeaderMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) {
                continue;
            }
            String val = cell.getStringCellValue();
            if (val != null && !val.isBlank()) {
                map.put(normalizeHeader(val), c);
            }
        }
        return map;
    }

    int findHeaderRowIndex(Sheet sheet) {
        int maxScan = Math.min(sheet.getLastRowNum(), 30);
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int nonEmpty = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK) {
                    String text = cell.toString().trim();
                    if (!text.isEmpty()) {
                        nonEmpty++;
                    }
                }
            }
            if (nonEmpty >= 3) {
                return r;
            }
        }
        return -1;
    }

    void removeColumn(Sheet sheet, int colIndex, int headerRowIdx) {
        for (int r = headerRowIdx; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = colIndex; c < row.getLastCellNum() - 1; c++) {
                Cell src = row.getCell(c + 1);
                Cell dest = row.getCell(c);
                if (dest == null) {
                    dest = row.createCell(c);
                }
                if (src == null) {
                    dest.setBlank();
                } else {
                    copyCellValue(src, dest);
                    if (src.getCellStyle() != null) {
                        dest.setCellStyle(src.getCellStyle());
                    }
                }
            }
            Cell last = row.getCell(row.getLastCellNum() - 1);
            if (last != null) {
                row.removeCell(last);
            }
        }
    }

    private void copyCellValue(Cell src, Cell dest) {
        switch (src.getCellType()) {
            case NUMERIC -> dest.setCellValue(src.getNumericCellValue());
            case BOOLEAN -> dest.setCellValue(src.getBooleanCellValue());
            case FORMULA -> dest.setCellFormula(src.getCellFormula());
            default -> dest.setCellValue(src.toString());
        }
    }

    String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }
}
