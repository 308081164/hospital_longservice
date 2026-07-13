package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationVersionGroupTest {

    @Test
    void buildKeySeparatesDifferentMonthlyFilesForSameHospital() {
        String aprilKey = ReconciliationVersionGroup.buildKey("嫒尚美", "嫒尚美4月账单.xlsx");
        String mayKey = ReconciliationVersionGroup.buildKey("嫒尚美", "嫒尚美5月账单.xlsx");

        assertThat(aprilKey).isNotEqualTo(mayKey);
        assertThat(aprilKey).isEqualTo("嫒尚美::嫒尚美4月账单.xlsx");
        assertThat(mayKey).isEqualTo("嫒尚美::嫒尚美5月账单.xlsx");
    }

    @Test
    void buildKeyKeepsSameUploadInOneVersionChain() {
        String first = ReconciliationVersionGroup.buildKey("嫒尚美", "嫒尚美4月账单.xlsx");
        String second = ReconciliationVersionGroup.buildKey(" 嫒尚美 ", "嫒尚美4月账单.xlsx");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void normalizeSourceFileNameStripsPathPrefix() {
        assertThat(ReconciliationVersionGroup.normalizeSourceFileName("C:\\uploads\\嫒尚美5月账单.xlsx"))
                .isEqualTo("嫒尚美5月账单.xlsx");
    }

    @Test
    void buildKeyUsesFallbackLabelsForMissingValues() {
        assertThat(ReconciliationVersionGroup.buildKey(null, null))
                .isEqualTo("(未命名)::(未命名)");
    }
}
