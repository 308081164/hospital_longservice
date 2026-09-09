package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackTypeRegistryTest {

    @Test
    void loadsSixteenPackTypes() {
        assertThat(PackTypeRegistry.allDefinitions()).hasSize(16);
    }

    @Test
    void matchesZsdAlias() {
        assertThat(PackTypeRegistry.match("器械包(ZSD)"))
                .map(PackTypeRegistry.PackTypeDefinition::canonical)
                .contains("器械包(zsd）");
    }

    @Test
    void extraPaperPlasticAllowsOnlyHighTempPaper() {
        var def = PackTypeRegistry.match("额外包(纸塑袋)").orElseThrow();
        assertThat(PackTypeRegistry.materialAllowed(def, "高温纸塑袋75*200")).isTrue();
        assertThat(PackTypeRegistry.materialAllowed(def, "无纺布-90×90")).isFalse();
        assertThat(PackTypeRegistry.materialAllowed(def, "")).isFalse();
    }

    @Test
    void lowTempPackTypeLocksSterilization() {
        assertThat(PackTypeRegistry.isLowTempPackType("器械包（低温等离子）")).isTrue();
        assertThat(PackTypeRegistry.isLowTempPackType("额外包(纸塑袋)")).isFalse();
    }

    @Test
    void classifiesMaterialWithSizeSuffix() {
        assertThat(PackTypeRegistry.classifyMaterial("无纺布-90×90-50g"))
                .isEqualTo(PackTypeRegistry.MaterialFamily.NON_WOVEN);
        assertThat(PackTypeRegistry.classifyMaterial("低温纸塑袋20cm"))
                .isEqualTo(PackTypeRegistry.MaterialFamily.LOW_TEMP_PAPER);
    }

    @Test
    void dressingPackAllowsBlankMaterial() {
        var def = PackTypeRegistry.match("敷料包").orElseThrow();
        assertThat(PackTypeRegistry.materialAllowed(def, "")).isTrue();
        assertThat(PackTypeRegistry.materialAllowed(def, "无")).isTrue();
    }

    @Test
    void unknownPackTypeDoesNotMatch() {
        assertThat(PackTypeRegistry.match("高温灭菌")).isEmpty();
    }

    @Test
    void matchesLegacyAcceptedTypeShorthands() {
        assertThat(PackTypeRegistry.match("额外包低温等离子"))
                .map(PackTypeRegistry.PackTypeDefinition::canonical)
                .contains("额外包（低温等离子）");
        assertThat(PackTypeRegistry.packTypeEquivalent("额外包低温等离子", "额外包(低温等离子)"))
                .isTrue();
        assertThat(PackTypeRegistry.packTypeEquivalent("单包装（低温老肯）", "单包装包(老肯低温)"))
                .isTrue();
        assertThat(PackTypeRegistry.packTypeEquivalent("单包装", "单包装包"))
                .isTrue();
        assertThat(PackTypeRegistry.packTypeEquivalent("额外包", "额外包(纸塑袋)"))
                .isTrue();
    }
}
