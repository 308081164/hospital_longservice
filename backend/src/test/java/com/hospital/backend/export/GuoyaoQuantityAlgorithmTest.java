package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuoyaoQuantityAlgorithmTest {

    private GuoyaoQuantityAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new GuoyaoQuantityAlgorithm();
    }

    @Test
    void prefersInstrumentCountWhenPresent() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setInstrumentCount(42);
        row.setPackCount(3);
        assertEquals(42, algorithm.computeQuantity(row));
    }

    @Test
    void estimatesFromPackCountForHighTemp() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("高温");
        row.setPackCount(5);
        assertEquals(50, algorithm.computeQuantity(row));
    }

    @Test
    void estimatesFromPackCountForLowTemp() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setType("低温灭菌");
        row.setPackCount(2);
        assertEquals(24, algorithm.computeQuantity(row));
    }
}
