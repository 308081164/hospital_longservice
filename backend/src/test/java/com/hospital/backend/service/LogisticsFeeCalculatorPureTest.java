package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsFeeCalculatorPureTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    @Test
    void fallsBackToGlobalFeeWhenNoCustomerLogisticsPolicy() throws Exception {
        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode logistics = (ObjectNode) base.path("logistics");
        logistics.put("enabled", true);
        logistics.put("feePerTrip", 60.0);

        List<Map<String, Object>> rows = List.of(
                Map.of("deliveryDate", "2026-07-01"),
                Map.of("deliveryDate", "2026-07-02"),
                Map.of("deliveryDate", "2026-07-03"));

        Optional<LogisticsFeeCalculator.Result> result = LogisticsFeeCalculator.compute(base, rows);

        assertThat(result).isPresent();
        assertThat(result.get().tripCount()).isEqualTo(3);
        assertThat(result.get().feePerTrip()).isEqualTo(60.0);
        assertThat(result.get().totalFee()).isEqualTo(180.0);
        assertThat(result.get().feeSource()).isEqualTo("global");
        assertThat(result.get().policyId()).isNull();
    }
}
