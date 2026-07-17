package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeductionCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void computesMonthlyEquipmentDeduction() throws Exception {
        ObjectNode compiled = mapper.createObjectNode();
        ArrayNode policies = compiled.putArray("billingPolicies");
        ObjectNode deduction = policies.addObject();
        deduction.put("policyType", "DEDUCTION");
        deduction.put("name", "设备抵扣");
        deduction.put("policyId", 2);
        deduction.putObject("params").put("monthlyAmount", 3270);

        DeductionCalculator.Result result = DeductionCalculator.compute(compiled).orElseThrow();

        assertThat(result.monthlyAmount()).isEqualTo(3270.0);
        assertThat(DeductionCalculator.toBreakdownMap(result).get("deductionAmount")).isEqualTo(-3270.0);
    }

    @Test
    void returnsEmptyWhenPolicyMissing() {
        ObjectNode compiled = mapper.createObjectNode();
        assertThat(DeductionCalculator.compute(compiled)).isEmpty();
    }
}
