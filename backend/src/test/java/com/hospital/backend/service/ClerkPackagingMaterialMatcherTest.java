package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkPackagingMaterialMatcherTest {

    @Test
    void resolvesCmTierFromWidthTimesHeight() {
        assertThat(ClerkPackagingMaterialMatcher.resolvePaperPlasticCmTier("高温纸塑袋300*320")).isEqualTo(25);
        assertThat(ClerkPackagingMaterialMatcher.resolvePaperPlasticCmTier("高温纸塑袋200*250")).isEqualTo(20);
        assertThat(ClerkPackagingMaterialMatcher.matches("25cm", "高温纸塑袋300*320")).isTrue();
    }

    @Test
    void nangangPaperBagSplitsAtTwentyCentimetres() {
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋＜20cm", "高温纸塑袋100*200")).isTrue();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋≥20cm", "高温纸塑袋100*200")).isFalse();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋＜20cm", "高温纸塑袋150*200")).isTrue();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋＜20cm", "高温纸塑袋190*250")).isTrue();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋≥20cm", "高温纸塑袋200*250")).isTrue();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋＜20cm", "高温纸塑袋200*250")).isFalse();
        assertThat(ClerkPackagingMaterialMatcher.matches("纸塑袋≥20cm", "高温纸塑袋300*320")).isTrue();
    }
}
