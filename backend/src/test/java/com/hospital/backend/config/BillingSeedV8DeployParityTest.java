package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.service.RuleFidelityTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void bingchengHuanzuanRulesExcludeDrillBitKeyword() throws Exception {
        JsonNode manifest = RuleFidelityTestSupport.manifest();
        JsonNode rules = manifest.path("customers").path("BINGCHENG-YM").path("productRules");
        for (String ruleName : new String[]{
                "冰城环钻包按件5.5",
                "冰城环钻包无纺布加价3",
                "冰城环钻包小件包装加价3",
                "环钻27.5"
        }) {
            JsonNode rule = null;
            for (JsonNode candidate : rules) {
                if (ruleName.equals(candidate.path("name").asText())) {
                    rule = candidate;
                    break;
                }
            }
            assertThat(rule).as("rule %s", ruleName).isNotNull();
            List<String> keywords = jsonTextArray(rule.path("keywords"));
            assertThat(keywords).as("keywords for %s", ruleName).doesNotContain("环钻");
            assertThat(keywords).as("keywords for %s", ruleName).contains("环钻包");
            assertThat(jsonTextArray(rule.path("excludeKeywords")))
                    .as("excludeKeywords for %s", ruleName)
                    .contains("环钻头");
        }
    }

    private static List<String> jsonTextArray(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> values.add(item.asText()));
        }
        return values;
    }
}
