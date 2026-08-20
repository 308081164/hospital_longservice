package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFidelityExcelGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<JsonNode> goldenCases() throws Exception {
        try (InputStream in = RuleFidelityExcelGoldenTest.class.getResourceAsStream("/rule-fidelity-excel-goldens.json")) {
            JsonNode root = MAPPER.readTree(in);
            Stream.Builder<JsonNode> builder = Stream.builder();
            for (JsonNode testCase : root.path("cases")) {
                String kind = testCase.path("kind").asText("");
                if ("negative".equals(kind) || testCase.path("junitRegression").asBoolean(false)) {
                    builder.add(testCase);
                }
            }
            return builder.build();
        }
    }

    @ParameterizedTest
    @MethodSource("goldenCases")
    void excelGoldenRowsMatchEngine(JsonNode testCase) throws Exception {
        String code = testCase.path("customerCode").asText("");
        String hospital = testCase.path("hospital").asText("测试医院");
        JsonNode row = testCase.path("row");
        JsonNode rules = code.isBlank()
                ? MAPPER.createObjectNode().set("billingProfile",
                MAPPER.createObjectNode().put("enabled", false))
                : RuleFidelityTestSupport.compileForCustomerCode(code);
        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> input = RuleFidelityTestSupport.rowFromJson(row, hospital);
        var result = engine.processRow(input);
        if ("expected".equals(testCase.path("kind").asText())) {
            assertThat(result.status).isEqualTo("warning");
            if (row.hasNonNull("expectedUnitPrice")) {
                assertThat(result.expectedUnitPrice).isEqualTo(row.path("expectedUnitPrice").asDouble());
            }
        } else {
            assertThat(result.status).isIn("unchanged", "warning");
        }
    }
}
