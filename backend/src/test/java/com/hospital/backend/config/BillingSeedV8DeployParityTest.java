package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.service.RuleFidelityTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSeedV8DeployParityTest {

    @Test
    void bingchengManifestReflectsV8RuleActivation() throws Exception {
        JsonNode manifest = RuleFidelityTestSupport.manifest();
        JsonNode rules = manifest.path("customers").path("BINGCHENG-YM").path("productRules");
        boolean legacyActive = false;
        boolean perPieceActive = false;
        boolean extraFeeActive = false;
        for (JsonNode rule : rules) {
            String name = rule.path("name").asText();
            if ("环钻27.5".equals(name)) {
                legacyActive = rule.path("isActive").asBoolean(false);
            }
            if ("冰城环钻包按件5.5".equals(name)) {
                perPieceActive = rule.path("isActive").asBoolean(false);
            }
            if ("冰城环钻包无纺布加价3".equals(name)) {
                extraFeeActive = rule.path("isActive").asBoolean(false);
            }
        }
        assertThat(legacyActive).isFalse();
        assertThat(perPieceActive).isTrue();
        assertThat(extraFeeActive).isTrue();
    }
}
