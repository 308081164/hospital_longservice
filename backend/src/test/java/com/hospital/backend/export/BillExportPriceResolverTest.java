package com.hospital.backend.export;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class BillExportPriceResolverTest {

    @Test
    void resolvesExportTotalWhenCorrectedTotalStillOriginal() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(5);
        row.setUnitPrice(239.76);
        row.setTotalPrice(1198.8);
        row.setExpectedUnitPrice(70.33);
        row.setCorrectedTotalPrice(1198.8);

        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(351.65);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(70.33);
    }

    @Test
    void derivesPerPieceUsingPerPackInstrumentCount() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(2);
        row.setInstrumentCount(10);
        row.setExpectedUnitPrice(22.0);
        row.setCorrectedTotalPrice(44.0);

        assertThat(BillExportPriceResolver.resolveTotalPrice(row)).isEqualTo(44.0);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(22.0);
        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isEqualTo(4.4);
    }

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

    @Test
    void derivesPerPiecePriceFromTotalWhenMultipleInstruments() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(2);
        row.setInstrumentCount(6);
        row.setCorrectedTotalPrice(105.6);
        row.setExpectedUnitPrice(52.8);
        row.setOriginal(Map.of("importUnitPrice", 52.8));

        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isCloseTo(8.8, offset(0.01));
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isCloseTo(52.8, offset(0.01));
    }

    @Test
    void derivesPerPiecePriceFromTotalAndInstrumentCount() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(2);
        row.setInstrumentCount(3);
        row.setCorrectedTotalPrice(105.48);
        row.setExpectedUnitPrice(52.74);

        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isCloseTo(52.74, offset(0.01));
        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isCloseTo(17.58, offset(0.01));
    }

    @Test
    void prefersImportUnitPriceForPerPieceColumn() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(1);
        row.setInstrumentCount(1);
        row.setExpectedUnitPrice(30.4);
        row.setCorrectedTotalPrice(30.4);
        row.setOriginal(Map.of("importUnitPrice", 22.4));

        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(30.4);
        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isEqualTo(22.4);
    }

    @Test
    void singleInstrumentPerPieceEqualsPackPriceWhenNoImport() {
        BillRowItem row = new BillRowItem();
        row.setPackCount(1);
        row.setInstrumentCount(1);
        row.setCorrectedTotalPrice(28.0);

        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isEqualTo(28.0);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(28.0);
    }

    @Test
    void entityRowPerPieceUsesOriginalUnitPrice() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setUnitPrice(22.4);
        row.setExpectedUnitPrice(30.4);
        row.setPackCount(1);
        row.setInstrumentCount(1);
        row.setCorrectedTotalPrice(30.4);

        assertThat(BillExportPriceResolver.resolvePerPiecePrice(row)).isEqualTo(22.4);
        assertThat(BillExportPriceResolver.resolveUnitPrice(row)).isEqualTo(30.4);
    }
}
