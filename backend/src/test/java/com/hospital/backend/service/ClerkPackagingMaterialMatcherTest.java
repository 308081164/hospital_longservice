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
}
