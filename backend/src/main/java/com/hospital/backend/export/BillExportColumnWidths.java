package com.hospital.backend.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 发货单汇总表列宽（Excel 字符单位）。
 * 类型、包名加宽到能单行放下常见中文；包类别号略收窄，使标准 8 列在 A4 横向、100% 缩放下仍能一页排开。
 */
public final class BillExportColumnWidths {

    /** 类型。原 12，现覆盖实测最长约 13 个汉字。 */
    public static final double TYPE_CHARS = 28.0;
    /** 包名。原 30，现覆盖绝大多数长包名（视觉宽度约 42）。 */
    public static final double PACK_NAME_CHARS = 42.0;
    /** 包类别号。原 18，数值列不需要那么宽，让出页宽给类型和包名。 */
    public static final double CATEGORY_NO_CHARS = 14.0;
    public static final double PACK_COUNT_CHARS = 10.0;
    public static final double UNIT_PRICE_CHARS = 12.0;

    /** 程序化模板中类型、包名的固定列号（0-based，F / H）。 */
    public static final int TYPE_COL = 5;
    public static final int PACK_NAME_COL = 7;

    private BillExportColumnWidths() {
    }

    public static int widthUnits(double chars) {
        return (int) Math.round(chars * 256);
    }

    public static void applyTemplateWidths(Sheet sheet, BillColumnLayout layout) {
        if (sheet == null) {
            return;
        }
        if (layout == null) {
            layout = BillColumnLayout.STANDARD_8COL;
        }
        sheet.setColumnWidth(0, widthUnits(2.0));
        sheet.setColumnWidth(1, widthUnits(13.0));
        sheet.setColumnWidth(2, widthUnits(2.0));
        sheet.setColumnWidth(3, widthUnits(15.5)); // D 发货日期
        sheet.setColumnWidth(4, widthUnits(15.5)); // E 发货单号
        sheet.setColumnWidth(TYPE_COL, widthUnits(TYPE_CHARS));
        sheet.setColumnWidth(6, widthUnits(CATEGORY_NO_CHARS)); // G 包类别号
        sheet.setColumnWidth(PACK_NAME_COL, widthUnits(PACK_NAME_CHARS));
        sheet.setColumnWidth(8, widthUnits(PACK_COUNT_CHARS)); // I 包数
        if (layout.isExtended()) {
            sheet.setColumnWidth(9, widthUnits(18.0));  // J 包装材料
            sheet.setColumnWidth(10, widthUnits(14.0)); // K 单包内器械数量/把
            sheet.setColumnWidth(11, widthUnits(12.0)); // L 单价（把）
            sheet.setColumnWidth(12, widthUnits(UNIT_PRICE_CHARS)); // M 单价
            sheet.setColumnWidth(13, widthUnits(12.0)); // N 总价
        } else {
            sheet.setColumnWidth(9, widthUnits(UNIT_PRICE_CHARS)); // J 单价
            sheet.setColumnWidth(10, widthUnits(12.0)); // K 总价
        }
    }

    /**
     * 自动列宽会低估汉字，把类型/包名压得比模板还窄。这里把这两列抬回可读下限。
     */
    public static void enforceReadableMinimums(Sheet sheet, Row headerRow) {
        if (sheet == null) {
            return;
        }
        int typeCol = findHeader(headerRow, "类型", TYPE_COL);
        int packCol = findHeader(headerRow, "包名", PACK_NAME_COL);
        ensureMin(sheet, typeCol, TYPE_CHARS);
        ensureMin(sheet, packCol, PACK_NAME_CHARS);
    }

    private static void ensureMin(Sheet sheet, int col, double chars) {
        if (col < 0) {
            return;
        }
        int min = widthUnits(chars);
        if (sheet.getColumnWidth(col) < min) {
            sheet.setColumnWidth(col, min);
        }
    }

    private static int findHeader(Row headerRow, String name, int fallback) {
        if (headerRow == null || name == null) {
            return fallback;
        }
        short last = headerRow.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                continue;
            }
            if (name.equals(cell.getStringCellValue().trim())) {
                return c;
            }
        }
        return fallback;
    }
}
