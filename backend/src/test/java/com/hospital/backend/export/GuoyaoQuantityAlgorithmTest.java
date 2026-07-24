package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuoyaoQuantityAlgorithmTest {

    private GuoyaoQuantityAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new GuoyaoQuantityAlgorithm();
    }

    @Test
    void prefersInstrumentCountForNonInstrumentPack() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("额外包(纸塑袋)");
        row.setInstrumentCount(42);
        row.setPackCount(3);
        assertEquals(42, algorithm.computeQuantity(row));
    }

    @Test
    void appliesFrM804ForInstrumentPack() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("器械包(ZSD)");
        row.setCorrectedTotalPrice(313.5);
        assertEquals(42, algorithm.computeInstrumentPackQuantity(313.5));
        assertEquals(42, algorithm.computeQuantity(row));
    }

    @Test
    void frM804ExampleWithRemainder() {
        assertEquals(42, algorithm.computeInstrumentPackQuantity(313.5));
    }

    @Test
    void skipsAlgorithmForDressingPack() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("敷料包(无纺布包)");
        row.setPackCount(4);
        row.setCorrectedTotalPrice(120.0);
        assertEquals(4, algorithm.computeQuantity(row));
        assertFalse(algorithm.isGuoyaoInstrumentPack(row));
    }

    @Test
    void skipsAlgorithmForLowTempSinglePack() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("低温单包装包(纸塑袋)");
        row.setPackCount(2);
        assertEquals(2, algorithm.computeQuantity(row));
        assertFalse(algorithm.isGuoyaoInstrumentPack(row));
    }

    @Test
    void detectsInstrumentPackType() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("器械包(ZSD)");
        assertTrue(algorithm.isGuoyaoInstrumentPack(row));
    }
}
