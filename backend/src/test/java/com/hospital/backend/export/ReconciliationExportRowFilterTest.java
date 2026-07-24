package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationExportRowFilterTest {

    private final ReconciliationExportRowFilter filter = new ReconciliationExportRowFilter();

    @Test
    void keepsPricedRowsForShengYy() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("测试包");
        row.setCorrectedTotalPrice(12.0);
        assertThat(filter.shouldIncludeForExport(row)).isTrue();
    }

    @Test
    void excludesBlankPackNameAndZeroTotals() {
        HospitalReconciliationRow blank = new HospitalReconciliationRow();
        blank.setPackName("  ");
        blank.setTotalPrice(0.0);
        assertThat(filter.shouldIncludeForExport(blank)).isFalse();

        HospitalReconciliationRow zero = new HospitalReconciliationRow();
        zero.setPackName("占位行");
        zero.setTotalPrice(0.0);
        zero.setCorrectedTotalPrice(0.0);
        assertThat(filter.shouldIncludeForExport(zero)).isFalse();
    }

    @Test
    void keepsZeroPriceOverrideRows() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("仅记录包");
        row.setTotalPrice(0.0);
        row.setPricingRule("0元仅记录");
        assertThat(filter.shouldIncludeForExport(row)).isTrue();
    }

    @Test
    void noOpForNonShengYyCustomer() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("x");
        row.setTotalPrice(0.0);
        assertThat(filter.apply("HRB-WY", java.util.List.of(row))).containsExactly(row);
    }
}
