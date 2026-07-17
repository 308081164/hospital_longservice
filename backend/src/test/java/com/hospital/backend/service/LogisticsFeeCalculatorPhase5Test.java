package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.LogisticsImport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsFeeCalculatorPhase5Test {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    @Test
    void computeFromImports_usesImportedTripDates() throws Exception {
        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode logistics = (ObjectNode) base.path("logistics");
        logistics.put("enabled", true);
        logistics.put("feePerTrip", 50.0);
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "LOGISTICS");
        policy.set("params", MAPPER.readTree("""
                {"feePerTrip":50,"tripSource":"import"}
                """));
        base.set("billingPolicies", MAPPER.createArrayNode().add(policy));

        List<LogisticsImport> imports = List.of(
                importOn("2026-07-01"),
                importOn("2026-07-03"));

        Optional<LogisticsFeeCalculator.Result> result =
                LogisticsFeeCalculator.compute(base, List.of(), imports);

        assertThat(result).isPresent();
        assertThat(result.get().tripCount()).isEqualTo(2);
        assertThat(result.get().totalFee()).isEqualTo(100.0);
        assertThat(result.get().tripSource()).isEqualTo("import");
    }

    @Test
    void billingWeekdays_filtersDeliveryDates() throws Exception {
        ObjectNode base = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        ObjectNode logistics = (ObjectNode) base.path("logistics");
        logistics.put("enabled", true);
        logistics.put("feePerTrip", 50.0);
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "LOGISTICS");
        policy.set("params", MAPPER.readTree("""
                {"feePerTrip":50,"billingWeekdays":[1,3,5]}
                """));
        base.set("billingPolicies", MAPPER.createArrayNode().add(policy));

        // 2026-07-01 Wed, 2026-07-02 Thu, 2026-07-03 Fri
        List<Map<String, Object>> rows = List.of(
                Map.of("deliveryDate", "2026-07-01"),
                Map.of("deliveryDate", "2026-07-02"),
                Map.of("deliveryDate", "2026-07-03"));

        Optional<LogisticsFeeCalculator.Result> result = LogisticsFeeCalculator.compute(base, rows);

        assertThat(result).isPresent();
        assertThat(result.get().tripCount()).isEqualTo(2);
    }

    private static LogisticsImport importOn(String date) {
        LogisticsImport item = new LogisticsImport();
        item.setTripDate(LocalDate.parse(date));
        item.setTripCount(1);
        return item;
    }
}
