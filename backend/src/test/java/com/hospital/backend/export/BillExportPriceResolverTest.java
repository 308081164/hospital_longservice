package com.hospital.backend.export;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillExportPriceResolverTest {

    @Test
    void prefersCorrectedTotalForBillRow() {
        BillRowItem row = new BillRowItem();
        row.setUnitPrice(22.0);
        row.setTotalPrice(22.0);
        row.setExpectedUnitPrice(16.5);
        row.setCorrectedTotalPrice(16.5);
        row.setPackCount(1);

        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(16.5);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(16.5);
    }

    @Test
    void derivesTotalFromExpectedUnitWhenCorrectedMissing() {
        BillRowItem row = new BillRowItem();
        row.setUnitPrice(0.0);
        row.setTotalPrice(0.0);
        row.setExpectedUnitPrice(8.0);
        row.setPackCount(3);

        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(24.0);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(8.0);
    }

    @Test
    void resolvesEntityRowSameAsBillItem() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setExpectedUnitPrice(5.5);
        row.setPackCount(10);

        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(55.0);
    }

    @Test
    void usesMatchedPriceOptionWhenExpectedUnitMissing() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackCount(3);
        row.setMatchedPriceOption(15.0);
        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(45.0);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(15.0);
    }

    @Test
    void prefersExpectedUnitPriceOverMatchedPriceOptionForEntityRow() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackCount(2);
        row.setExpectedUnitPrice(13.5);
        row.setMatchedPriceOption(110.0);
        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(27.0);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(13.5);
    }
}
