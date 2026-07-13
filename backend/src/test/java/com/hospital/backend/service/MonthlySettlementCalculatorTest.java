package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlySettlementCalculatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appliesMinChargeWhenSterilizeTotalBelowThreshold() throws Exception {
        ObjectNode rules = rulesWithMonthlyPolicy(8000.0, null);
        Optional<MonthlySettlementCalculator.Result> result =
                MonthlySettlementCalculator.compute(rules, 6500);

        assertThat(result).isPresent();
        assertThat(result.get().rawSterilizeTotal()).isEqualTo(6500.0);
        assertThat(result.get().adjustedTotal()).isEqualTo(8000.0);
        assertThat(result.get().adjustment()).isEqualTo(1500.0);
        assertThat(result.get().minCharge()).isEqualTo(8000.0);
    }

    @Test
    void appliesMaxCapWhenSterilizeTotalAboveThreshold() throws Exception {
        ObjectNode rules = rulesWithMonthlyPolicy(null, 8000.0);
        Optional<MonthlySettlementCalculator.Result> result =
                MonthlySettlementCalculator.compute(rules, 9500);

        assertThat(result).isPresent();
        assertThat(result.get().adjustedTotal()).isEqualTo(8000.0);
        assertThat(result.get().adjustment()).isEqualTo(-1500.0);
        assertThat(result.get().maxCap()).isEqualTo(8000.0);
    }

    @Test
    void returnsEmptyWhenNoMonthlyPolicyConfigured() {
        ObjectNode rules = MAPPER.createObjectNode();
        assertThat(MonthlySettlementCalculator.compute(rules, 5000)).isEmpty();
    }

    private static ObjectNode rulesWithMonthlyPolicy(Double minCharge, Double maxCap) {
        ObjectNode rules = MAPPER.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "MONTHLY_SETTLEMENT");
        policy.put("policyId", 1L);
        policy.put("name", "月度结算");
        ObjectNode params = policy.putObject("params");
        if (minCharge != null) {
            params.put("minCharge", minCharge);
        }
        if (maxCap != null) {
            params.put("maxCap", maxCap);
        }
        return rules;
    }
}
