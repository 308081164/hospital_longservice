package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UrgentFeeCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void computesXinfahongshiziUrgentFees() throws Exception {
        ObjectNode compiled = mapper.createObjectNode();
        ArrayNode policies = compiled.putArray("billingPolicies");
        ObjectNode urgent = policies.addObject();
        urgent.put("policyType", "URGENT");
        urgent.put("name", "新发红十字加急");
        urgent.put("policyId", 1);
        ObjectNode params = urgent.putObject("params");
        params.put("baseMultiplier", 1.25);
        params.put("adjustedMultiplier", 1.025);
        params.put("urgentLogisticsFeePerTrip", 150);
        params.put("urgentLogisticsDiscountRate", 0.9);

        List<Map<String, Object>> rows = List.of(
                row("2024-01-02", 1000, true),
                row("2024-01-02", 500, true),
                row("2024-01-03", 800, false)
        );

        UrgentFeeCalculator.Result result = UrgentFeeCalculator.compute(compiled, rows).orElseThrow();

        assertThat(result.urgentBaseTotal()).isEqualTo(1500.0);
        assertThat(result.urgentRowCount()).isEqualTo(2);
        assertThat(result.nominalSurcharge()).isEqualTo(375.0);
        assertThat(result.adjustedSurcharge()).isEqualTo(37.5);
        assertThat(result.urgentTripCount()).isEqualTo(1);
        assertThat(result.nominalUrgentLogisticsTotal()).isEqualTo(150.0);
        assertThat(result.adjustedUrgentLogisticsTotal()).isEqualTo(135.0);
    }

    @Test
    void returnsEmptyWhenNoUrgentRows() throws Exception {
        ObjectNode compiled = mapper.createObjectNode();
        ArrayNode policies = compiled.putArray("billingPolicies");
        ObjectNode urgent = policies.addObject();
        urgent.put("policyType", "URGENT");
        urgent.putObject("params").put("baseMultiplier", 1.25);

        List<Map<String, Object>> rows = List.of(row("2024-01-02", 1000, false));

        assertThat(UrgentFeeCalculator.compute(compiled, rows)).isEmpty();
    }

    private static Map<String, Object> row(String date, double total, boolean urgent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deliveryDate", date);
        row.put("correctedTotalPrice", total);
        row.put("isUrgent", urgent);
        return row;
    }
}
