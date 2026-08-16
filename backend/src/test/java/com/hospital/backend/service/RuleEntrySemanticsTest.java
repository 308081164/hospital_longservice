package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEntrySemanticsTest {

    private static final Set<String> ALLOWED_RULE_TYPES = Set.of(
            "FIXED_PRICE", "PRICE_PER_INSTRUMENT", "EXTRA_FEE", "ADD_FEE",
            "FOLD", "MULTIPLIER", "ZERO_PRICE_OVERRIDE"
    );

    @Test
    void manifestActiveRulesHaveUnambiguousCoreFields() throws Exception {
        JsonNode customers = PricingEngineTestSupport.manifest().path("customers");
        customers.fields().forEachRemaining(entry -> {
            String code = entry.getKey();
            JsonNode customer = entry.getValue();
            for (JsonNode rule : customer.path("productRules")) {
                if (!rule.path("isActive").asBoolean(true)) {
                    continue;
                }
                String ruleType = rule.path("ruleType").asText("");
                assertThat(ruleType).as("customer %s ruleType", code).isIn(ALLOWED_RULE_TYPES);
                assertThat(rule.path("name").asText("")).as("customer %s rule name", code).isNotBlank();
                switch (ruleType) {
                    case "FIXED_PRICE", "PRICE_PER_INSTRUMENT" ->
                            assertThat(rule.has("price")).as("customer %s %s", code, ruleType).isTrue();
                    case "EXTRA_FEE", "ADD_FEE" ->
                            assertThat(rule.has("fee")).as("customer %s EXTRA_FEE", code).isTrue();
                    case "FOLD" -> {
                        assertThat(rule.has("threshold")).as("customer %s FOLD threshold", code).isTrue();
                        assertThat(rule.has("foldRatio")).as("customer %s FOLD foldRatio", code).isTrue();
                    }
                    case "MULTIPLIER" ->
                            assertThat(rule.has("multiplier")).as("customer %s MULTIPLIER", code).isTrue();
                    default -> { }
                }
                if (rule.has("minInstrumentCount") && rule.has("maxInstrumentCount")) {
                    assertThat(rule.path("minInstrumentCount").asInt())
                            .as("customer %s min/max", code)
                            .isLessThanOrEqualTo(rule.path("maxInstrumentCount").asInt());
                }
            }
        });
    }

    @Test
    void registryCoversAllSc11TypesFromExcel() throws Exception {
        JsonNode registry = PricingEngineTestSupport.registry();
        Set<String> types = StreamSupport.stream(registry.path("entries").path("hospital").spliterator(), false)
                .map(n -> n.path("sc11Type").asText())
                .collect(Collectors.toSet());
        types.addAll(StreamSupport.stream(registry.path("entries").path("generic").spliterator(), false)
                .map(n -> n.path("sc11Type").asText())
                .collect(Collectors.toSet()));
        types.add("SC11-T16");
        assertThat(types).contains(
                "SC11-T01", "SC11-T02", "SC11-T03", "SC11-T03b", "SC11-T04", "SC11-T04b",
                "SC11-T05", "SC11-T06", "SC11-T07", "SC11-T08", "SC11-T09", "SC11-T10",
                "SC11-T11", "SC11-T12", "SC11-T13", "SC11-T14", "SC11-T15", "SC11-T16"
        );
    }

    @Test
    void registryCountsMatchExcelStructure() throws Exception {
        JsonNode registry = PricingEngineTestSupport.registry();
        assertThat(registry.path("counts").path("hospitalRules").asInt()).isEqualTo(53);
        assertThat(registry.path("counts").path("genericRules").asInt()).isEqualTo(22);
        assertThat(registry.path("counts").path("tierRules").asInt()).isGreaterThanOrEqualTo(20);
    }
}
