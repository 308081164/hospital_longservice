package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpecialCharge11CoverageTest {

    static Stream<JsonNode> sc11Fixtures() throws Exception {
        return StreamSupport.stream(PricingEngineTestSupport.fixtures().path("fixtures").spliterator(), false)
                .filter(PricingEngineTestSupport::isRunnableFixture);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sc11Fixtures")
    void sc11FixtureMatchesEngine(JsonNode fixture) throws Exception {
        String id = fixture.path("id").asText();
        String customerCode = fixture.path("customerCode").asText();
        JsonNode expect = fixture.path("expect");

        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode(customerCode);
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.rowFromFixture(fixture));

        if (expect.hasNonNull("status")) {
            assertThat(result.status).as(id).isEqualTo(expect.path("status").asText());
        }
        if (expect.hasNonNull("expectedUnitPrice")) {
            assertThat(result.expectedUnitPrice).as(id)
                    .isCloseTo(expect.path("expectedUnitPrice").asDouble(), within(0.05));
        }
        if (expect.hasNonNull("pricingRuleContains")) {
            assertThat(result.pricingRule).as(id).contains(expect.path("pricingRuleContains").asText());
        }
        if (expect.hasNonNull("pricingRuleNotContains")) {
            assertThat(result.pricingRule).as(id).doesNotContain(expect.path("pricingRuleNotContains").asText());
        }
        if (expect.hasNonNull("notesContains")) {
            assertThat(result.notes).as(id).anyMatch(n -> n.contains(expect.path("notesContains").asText()));
        }
    }
}
