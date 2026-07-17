package com.hospital.backend.export;

public enum ExportType {
    BILL("bill"),
    SETTLEMENT("settlement"),
    DEPT_SUMMARY("dept_summary"),
    PRICE_SUMMARY("price_summary"),
    INSTRUMENT_AUDIT("instrument_audit"),
    DAILY("daily");

    private final String code;

    ExportType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ExportType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return BILL;
        }
        for (ExportType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown export type: " + code);
    }
}
