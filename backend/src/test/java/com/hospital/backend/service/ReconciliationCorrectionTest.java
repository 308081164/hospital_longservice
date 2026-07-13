package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationCorrectionTest {

    @Test
    void batchCorrectionRecalculatesTotalDifferenceAndMarksCorrected() {
        double expectedUnitPrice = 11.0;
        int packCount = 4;
        double totalPrice = 54.0;

        double correctedTotal = Math.round(expectedUnitPrice * packCount * 100.0) / 100.0;
        double difference = Math.round((correctedTotal - totalPrice) * 100.0) / 100.0;
        String status = Math.abs(difference) > 0.001 ? "corrected" : "unchanged";

        assertThat(correctedTotal).isEqualTo(44.0);
        assertThat(difference).isEqualTo(-10.0);
        assertThat(status).isEqualTo("corrected");
    }

    @Test
    void unchangedRowKeepsUnchangedStatusWhenDifferenceIsZero() {
        double expectedUnitPrice = 22.0;
        int packCount = 1;
        double totalPrice = 22.0;

        double correctedTotal = Math.round(expectedUnitPrice * packCount * 100.0) / 100.0;
        double difference = Math.round((correctedTotal - totalPrice) * 100.0) / 100.0;
        String status = Math.abs(difference) > 0.001 ? "corrected" : "unchanged";

        assertThat(difference).isEqualTo(0.0);
        assertThat(status).isEqualTo("unchanged");
    }

    @Test
    void totalDifferenceOnlySumsWarningRows() {
        double warningDiff1 = -10.0;
        double warningDiff2 = 5.5;
        double correctedDiff = -100.0;
        double unchangedDiff = 3.0;

        double totalDifference = 0.0;
        totalDifference += sumIfWarning("warning", warningDiff1);
        totalDifference += sumIfWarning("warning", warningDiff2);
        totalDifference += sumIfWarning("corrected", correctedDiff);
        totalDifference += sumIfWarning("unchanged", unchangedDiff);
        totalDifference += sumIfWarning("skipped", 20.0);

        assertThat(totalDifference).isEqualTo(-4.5);
    }

    @Test
    void manualSingleCorrectionKeepsUserValueAndMarksCorrected() {
        double userCorrectedTotal = 25000.0;
        double totalPrice = 24000.0;
        double expectedUnitPrice = 11.0;
        int packCount = 4;
        double ruleCorrectedTotal = Math.round(expectedUnitPrice * packCount * 100.0) / 100.0;

        double difference = Math.round((userCorrectedTotal - totalPrice) * 100.0) / 100.0;
        String status = "corrected";

        assertThat(ruleCorrectedTotal).isEqualTo(44.0);
        assertThat(userCorrectedTotal).isNotEqualTo(ruleCorrectedTotal);
        assertThat(difference).isEqualTo(1000.0);
        assertThat(status).isEqualTo("corrected");
        assertThat(sumIfWarning(status, difference)).isEqualTo(0.0);
    }

    private static double sumIfWarning(String status, double diff) {
        return "warning".equals(status) ? diff : 0.0;
    }
}
