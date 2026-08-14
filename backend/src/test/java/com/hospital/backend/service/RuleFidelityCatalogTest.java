package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFidelityCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void manifestShowsBingchengLegacyHuanzuanRuleInactive() throws Exception {
        JsonNode manifest = RuleFidelityTestSupport.manifest();
        JsonNode rules = manifest.path("customers").path("BINGCHENG-YM").path("productRules");
        boolean legacyActive = false;
        boolean v8Active = false;
        for (JsonNode rule : rules) {
            if ("环钻27.5".equals(rule.path("name").asText())) {
                legacyActive = rule.path("isActive").asBoolean(false);
            }
            if ("冰城环钻包按件5.5".equals(rule.path("name").asText())) {
                v8Active = rule.path("isActive").asBoolean(false);
            }
        }
        assertThat(legacyActive).isFalse();
        assertThat(v8Active).isTrue();
    }

    static Stream<JsonNode> bingchengV8Cases() throws Exception {
        try (InputStream in = RuleFidelityCatalogTest.class.getResourceAsStream("/rule-fidelity-catalog.json")) {
            JsonNode root = MAPPER.readTree(in);
            JsonNode cases = root.path("bingchengV8Cases");
            Stream.Builder<JsonNode> builder = Stream.builder();
            cases.forEach(builder::add);
            return builder.build();
        }
    }

    @ParameterizedTest
    @MethodSource("bingchengV8Cases")
    void bingchengV8CasesMatchManifestRules(JsonNode testCase) throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BINGCHENG-YM");
        PricingEngine engine = new PricingEngine(rules);
        var result = engine.processRow(java.util.Map.of(
                "hospitalName", testCase.path("hospital").asText(),
                "department", "手术室",
                "type", "高温无纺布-90×90-50g",
                "packName", testCase.path("packName").asText(),
                "packageMaterial", "无纺布-90×90-50g",
                "instrumentCount", testCase.path("instrumentCount").asInt(),
                "packCount", testCase.path("packCount").asInt(),
                "unitPrice", testCase.path("unitPrice").asDouble(),
                "totalPrice", testCase.path("unitPrice").asDouble() * testCase.path("packCount").asInt()
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(testCase.path("expectedUnitPrice").asDouble());
        assertThat(result.pricingRule).doesNotContain(testCase.path("mustNotHitRule").asText());
    }
}
