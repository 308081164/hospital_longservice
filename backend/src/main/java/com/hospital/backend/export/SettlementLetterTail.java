package com.hospital.backend.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * 结款函模板按两行费用排正文。费用只剩一行时，先把正文挪到新的宽行，再拆开原来的合并格。
 */
public final class SettlementLetterTail {

    private SettlementLetterTail() {
    }

    public static void relocateWhenCompacted(XSSFSheet sheet, int contentRow, int saluteRow, int closingRow) {
        move(sheet, 17, contentRow);
        move(sheet, 18, saluteRow);
        move(sheet, 19, closingRow);
    }

    private static void move(XSSFSheet sheet, int fromRow, int toRow) {
        if (fromRow == toRow) {
            return;
        }
        Row sourceRow = sheet.getRow(fromRow - 1);
        if (sourceRow == null) {
            return;
        }
        Row destRow = sheet.getRow(toRow - 1);
        if (destRow == null) {
            destRow = sheet.createRow(toRow - 1);
        }
        if (sourceRow.getHeight() > destRow.getHeight()) {
            destRow.setHeight(sourceRow.getHeight());
        }
        for (int col = 3; col <= 8; col++) {
            Cell source = sourceRow.getCell(col);
            if (source == null || cellText(source).isBlank()) {
                continue;
            }
            Cell dest = destRow.getCell(col);
            if (dest == null) {
                dest = destRow.createCell(col);
            }
            if (cellText(dest).isBlank()) {
                dest.setCellValue(cellText(source));
                dest.setCellStyle(source.getCellStyle());
            }
            source.setBlank();
        }
    }

    private static String cellText(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue() == null ? "" : cell.getStringCellValue();
        }
        return cell.toString() == null ? "" : cell.toString();
    }
}
