package com.hospital.backend.export;

import org.apache.poi.ss.util.CellReference;

/**
 * Bill export column layout descriptor (standard 8-col vs 附一 extended 11-col).
 */
public enum BillColumnLayout {

    STANDARD_8COL(
            "standard_8col",
            10,
            new String[]{
                    "发货日期", "发货单号", "类型", "包类别号", "包名", "包数", "单价", "总价"
            },
            new String[][]{
                    {"发货日期", "灭菌日期"},
                    {"发货单号", "灭菌锅次"},
                    {"类型"},
                    {"包类别号", "病人ID"},
                    {"包名", "器械名称"},
                    {"包数"},
                    {"单价"},
                    {"总价"},
            }),

    FUYI_EXTENDED_11COL(
            "fuyi_extended_11col",
            13,
            new String[]{
                    "发货日期", "发货单号", "类型", "包类别号", "包名", "包数",
                    "包装材料", "单包内器械数量/把", "单价（把）", "单价", "总价"
            },
            new String[][]{
                    {"发货日期", "灭菌日期"},
                    {"发货单号", "灭菌锅次"},
                    {"类型"},
                    {"包类别号", "病人ID"},
                    {"包名", "器械名称"},
                    {"包数"},
                    {"包装材料"},
                    {"单包内器械数量/把", "器械数"},
                    {"单价（把）"},
                    {"单价"},
                    {"总价"},
            });

    public static final String KEY_STANDARD = "standard_8col";
    public static final String KEY_FUYI_EXTENDED = "fuyi_extended_11col";

    private final String key;
    private final int maxColIndex;
    private final String[] headers;
    private final String[][] headerAliases;

    BillColumnLayout(String key, int maxColIndex, String[] headers, String[][] headerAliases) {
        this.key = key;
        this.maxColIndex = maxColIndex;
        this.headers = headers;
        this.headerAliases = headerAliases;
    }

    public String getKey() {
        return key;
    }

    /** 0-indexed last column (K=10, N=13). */
    public int getMaxColIndex() {
        return maxColIndex;
    }

    public String[] getHeaders() {
        return headers;
    }

    public String[][] getHeaderAliases() {
        return headerAliases;
    }

    public String maxColLetter() {
        return CellReference.convertNumToColString(maxColIndex);
    }

    /** 0-indexed column of 包数 (I=8 in both layouts). */
    public int packCountColIndex() {
        return 8;
    }

    /** 0-indexed column of 总价 (K=10 standard, N=13 fuyi). */
    public int totalPriceColIndex() {
        return maxColIndex;
    }

    /** 0-indexed column of 单价 (J=9 standard, M=12 fuyi). */
    public int unitPriceColIndex() {
        return isExtended() ? 12 : 9;
    }

    /**
     * 附一 11 列布局固定列位（D=3 … N=13），避免表头重名 map 覆盖。
     * 标准 8 列返回 null，仍走 header 别名解析。
     */
    public Integer fixedDataColumnIndex(String logicalHeader) {
        if (!isExtended()) {
            return null;
        }
        return switch (logicalHeader) {
            case "包数" -> 8;
            case "包装材料" -> 9;
            case "单包内器械数量/把", "器械数" -> 10;
            case "单价（把）" -> 11;
            case "单价" -> 12;
            case "总价" -> 13;
            default -> null;
        };
    }

    public boolean isExtended() {
        return this == FUYI_EXTENDED_11COL;
    }

    public static BillColumnLayout fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return STANDARD_8COL;
        }
        String normalized = raw.trim().toLowerCase();
        if (KEY_FUYI_EXTENDED.equals(normalized)) {
            return FUYI_EXTENDED_11COL;
        }
        return STANDARD_8COL;
    }
}
