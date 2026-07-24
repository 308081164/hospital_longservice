package com.hospital.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostProcessBillExportAutoSizeTest {

    @Test
    void shouldAutoSizeWhenRowCountWithinThreshold() {
        assertThat(HospitalReconciliationServiceImpl.shouldAutoSizeBillExportColumns(2000)).isTrue();
        assertThat(HospitalReconciliationServiceImpl.shouldAutoSizeBillExportColumns(1500)).isTrue();
        assertThat(HospitalReconciliationServiceImpl.shouldAutoSizeBillExportColumns(1)).isTrue();
    }

    @Test
    void shouldSkipAutoSizeWhenRowCountExceedsThreshold() {
        assertThat(HospitalReconciliationServiceImpl.shouldAutoSizeBillExportColumns(2001)).isFalse();
        assertThat(HospitalReconciliationServiceImpl.shouldAutoSizeBillExportColumns(5005)).isFalse();
    }
}
