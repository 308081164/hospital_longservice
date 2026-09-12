package com.hospital.backend.service;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationAnomalyDetectorTest {

    @Test
    void detectsUnitPriceMismatch() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setStatus("unchanged");
        row.setUnitPrice(25.0);
        row.setExpectedUnitPrice(44.0);
        row.setPricingRule("高温无纺布计费");
        assertThat(ReconciliationAnomalyDetector.isAnomalyRow(row, false)).isTrue();
    }

    @Test
    void detectsNoRuleHit() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setStatus("unchanged");
        row.setPricingRule("未命中规则");
        assertThat(ReconciliationAnomalyDetector.isAnomalyRow(row, false)).isTrue();
    }
}
