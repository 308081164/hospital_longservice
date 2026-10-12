package com.hospital.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SheetIssueCountTest {

    @Test
    void correctedRowsStillCountAsDepartmentIssues() {
        assertThat(HospitalReconciliationServiceImpl.countsTowardSheetIssue("warning")).isTrue();
        assertThat(HospitalReconciliationServiceImpl.countsTowardSheetIssue("corrected")).isTrue();
        assertThat(HospitalReconciliationServiceImpl.countsTowardSheetIssue("unchanged")).isFalse();
    }
}
