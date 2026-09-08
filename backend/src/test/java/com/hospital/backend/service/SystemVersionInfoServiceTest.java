package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SystemVersionInfoServiceTest {

    @Test
    void latestUpdatedAtDisplay_prefersNewestInstant() {
        String result = SystemVersionInfoService.latestUpdatedAtDisplay(
                "2026-09-04T09:46:00Z",
                "2026-09-08T08:00:00Z",
                "2026-09-08T09:45:00Z",
                "",
                "OK baseline-sync 2026-09-08T09:50:00Z");
        assertEquals("2026-09-08 17:50", result);
    }

    @Test
    void latestUpdatedAtDisplay_fallsBackToBuildTimeWhenOthersMissing() {
        String result = SystemVersionInfoService.latestUpdatedAtDisplay(
                "2026-09-04T09:46:00Z",
                "",
                "",
                "",
                "");
        assertEquals("2026-09-04 17:46", result);
    }

    @Test
    void parseReconcileStatusInstant_extractsEmbeddedTimestamp() {
        Instant instant = SystemVersionInfoService.parseReconcileStatusInstant(
                "OK baseline-sync 2026-09-08T09:45:12.345Z");
        assertNotNull(instant);
        assertEquals("2026-09-08T09:45:12.345Z", instant.toString());
    }
}
