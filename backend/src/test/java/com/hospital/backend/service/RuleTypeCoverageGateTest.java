package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleTypeCoverageGateTest {

    private static final List<String> REQUIRED_SC11_TYPES = List.of(
            "SC11-T01", "SC11-T02", "SC11-T03", "SC11-T03b", "SC11-T04", "SC11-T04b",
            "SC11-T05", "SC11-T06", "SC11-T07", "SC11-T08", "SC11-T09", "SC11-T10",
            "SC11-T11", "SC11-T12", "SC11-T13", "SC11-T14", "SC11-T15", "SC11-T16"
    );

    @Test
    void pendingSc11BillingEvidenceTypesAreDocumented() throws Exception {
        List<String> missing = PricingEngineTestSupport.sc11TypesMissingConfirmedEvidence();
        assertThat(missing)
                .as("documented pending types — enable everySc11TypeHasConfirmedBillingEvidenceFixture when resolved")
                .isNotEmpty();
        assertThat(missing).contains("SC11-T02", "SC11-T04", "SC11-T16");
    }

    @Test
    @Disabled("待补账单实践：13 类 SC11 尚无 confirmed fixture，见 docs/sc11-billing-evidence-audit.md §Invalid")
    void everySc11TypeHasConfirmedBillingEvidenceFixture() throws Exception {
        Map<String, Integer> confirmed = PricingEngineTestSupport.confirmedFixtureTypeCounts();
        List<String> missing = PricingEngineTestSupport.sc11TypesMissingConfirmedEvidence();
        assertThat(missing)
                .as("types without confirmed billing fixture — see docs/sc11-billing-evidence-audit.md §Invalid")
                .isEmpty();
        for (String type : REQUIRED_SC11_TYPES) {
            assertThat(confirmed.getOrDefault(type, 0))
                    .as("confirmed billing fixture count for %s", type)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void registryTypesAreSubsetOfDocumentedSc11Types() throws Exception {
        JsonNode registry = PricingEngineTestSupport.registry();
        Set<String> required = new HashSet<>(REQUIRED_SC11_TYPES);
        registry.path("sc11TypeCounts").fields().forEachRemaining(entry ->
                assertThat(required).contains(entry.getKey()));
    }

    @Test
    void allRunnableSc11FixturesExecuteWithoutException() throws Exception {
        for (JsonNode fixture : PricingEngineTestSupport.fixtures().path("fixtures")) {
            if (!PricingEngineTestSupport.isRunnableFixture(fixture)) {
                continue;
            }
            String customerCode = fixture.path("customerCode").asText();
            PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode(customerCode);
            PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.rowFromFixture(fixture));
            assertThat(result.status).as(fixture.path("id").asText()).isIn("unchanged", "warning", "error");
        }
    }
}
