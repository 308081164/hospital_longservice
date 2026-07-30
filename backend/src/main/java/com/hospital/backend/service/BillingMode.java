package com.hospital.backend.service;

/**
 * 特殊固定价规则的计价单位模式。
 */
public enum BillingMode {
    /** 单价 = price，总价 = price × packCount */
    PER_PACK,
    /** 单价 = price × pieceCount，pieceCount 来自行件数或 ZSD 单包件数 */
    PER_INSTRUMENT,
    /** 单价 = price × 包名后缀数字 */
    PACK_NAME_SUFFIX;

    public static BillingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BillingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
