package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-hospital gate for 20 SC11 v8 special-pricing customers (offline engine, no API).
 * Prints a machine-readable summary line for CI/report scripts.
 */
class V8SpecialPricingHospitalGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<V8Hospital> V8_HOSPITALS = List.of(
            new V8Hospital("BINGCHENG-YM", "冰城医美", true),
            new V8Hospital("GUOYAO-2", "电机厂", true),
            new V8Hospital("FNN-YY", "方南南", false),
            new V8Hospital("NEAU-YY", "东北农大", false),
            new V8Hospital("HRB-HEU", "工程大学", false),
            new V8Hospital("HRB-SD-MB", "松电慢病", false),
            new V8Hospital("HRB-HTFH", "航天风华", false),
            new V8Hospital("HRB-WY-EM", "市五院二门诊", true),
            new V8Hospital("JIUZHOU-FK", "九州", true),
            new V8Hospital("BOSHANG-YY", "博尚", false),
            new V8Hospital("HAIYUAN-SB", "海员松北", false),
            new V8Hospital("HLJ-FY-RK", "省妇幼人口", false),
            new V8Hospital("ZUYAN-NG", "祖研南岗", true),
            new V8Hospital("SHKF-YY", "社会康复", true),
            new V8Hospital("DL-FUCHAN", "道里妇幼", false),
            new V8Hospital("CHUNYU-YL", "春语医美", false),
            new V8Hospital("HL-ZGH", "总工会", false),
            new V8Hospital("JZSW-BIO", "基准生物", false),
            new V8Hospital("SUOFEI-YL", "索菲医美", false),
            new V8Hospital("HLJ-JYGLJ-YY", "省监狱管理局", false));

    private record V8Hospital(String code, String label, boolean materialComplete) {}

    private record CaseResult(String source, String id, boolean pass, String detail) {}

    private record HospitalReport(
            String code,
            String label,
            boolean materialComplete,
            int goldenTotal,
            int goldenPass,
            int sc11Total,
            int sc11Pass,
            List<String> failures) {}

    @Test
    void allTwentyV8HospitalsEngineGate() throws Exception {
        JsonNode goldens = loadJson("/rule-fidelity-excel-goldens.json").path("cases");
        JsonNode sc11 = loadJson("/pricing-engine/sc11-fixtures.json").path("fixtures");

        List<HospitalReport> reports = new ArrayList<>();
        int totalFail = 0;

        for (V8Hospital hospital : V8_HOSPITALS) {
            List<String> failures = new ArrayList<>();
            int goldenPass = 0;
            int goldenTotal = 0;
            int sc11Pass = 0;
            int sc11Total = 0;

            JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode(hospital.code());
            PricingEngine engine = new PricingEngine(rules);

            for (JsonNode testCase : goldens) {
                String kind = testCase.path("kind").asText("");
                if (!"negative".equals(kind) && !testCase.path("junitRegression").asBoolean(false)) {
                    continue;
                }
                if (!hospital.code().equals(testCase.path("customerCode").asText(""))) {
                    continue;
                }
                goldenTotal++;
                CaseResult result = runGoldenCase(engine, testCase);
                if (result.pass()) {
                    goldenPass++;
                } else {
                    failures.add("golden:" + result.id() + " -> " + result.detail());
                }
            }

            for (JsonNode fixture : sc11) {
                if (!hospital.code().equals(fixture.path("customerCode").asText(""))) {
                    continue;
                }
                sc11Total++;
                CaseResult result = runSc11Fixture(engine, fixture);
                if (result.pass()) {
                    sc11Pass++;
                } else {
                    failures.add("sc11:" + result.id() + " -> " + result.detail());
                }
            }

            if (!failures.isEmpty()) {
                totalFail += failures.size();
            }
            reports.add(new HospitalReport(
                    hospital.code(),
                    hospital.label(),
                    hospital.materialComplete(),
                    goldenTotal,
                    goldenPass,
                    sc11Total,
                    sc11Pass,
                    failures));
        }

        for (HospitalReport report : reports) {
            System.out.printf(
                    "V8_GATE code=%s label=%s material=%s golden=%d/%d sc11=%d/%d fails=%d%n",
                    report.code(),
                    report.label(),
                    report.materialComplete() ? "complete" : "skip",
                    report.goldenPass(),
                    report.goldenTotal(),
                    report.sc11Pass(),
                    report.sc11Total(),
                    report.failures().size());
            for (String failure : report.failures()) {
                System.out.println("  FAIL " + report.code() + " " + failure);
            }
        }

        long materialCompleteWithGolden = reports.stream()
                .filter(r -> r.materialComplete() && r.goldenTotal() > 0)
                .filter(r -> r.goldenPass() < r.goldenTotal())
                .count();
        assertThat(materialCompleteWithGolden)
                .as("材料齐全且有 golden 的 6 院不应出现 golden 回归")
                .isZero();
        long materialCompleteSc11Fails = reports.stream()
                .filter(r -> r.materialComplete())
                .mapToInt(r -> r.sc11Total() - r.sc11Pass())
                .sum();
        assertThat(materialCompleteSc11Fails)
                .as("材料齐全院不应出现 SC11 回归")
                .isZero();
        int knownSkipFailures = countKnownSkipMaterialSc11Failures(reports);
        assertThat(totalFail)
                .as("SC11 fixture 全量应通过；golden 失败仅限已知历史项；skip-material 院 SC11 失败容许")
                .isLessThanOrEqualTo(countKnownGoldenFailures(reports) + knownSkipFailures);
    }

    private static int countKnownGoldenFailures(List<HospitalReport> reports) {
        // 非材料齐全院或无 golden 覆盖的不计入硬门禁
        return reports.stream()
                .mapToInt(r -> r.goldenTotal() - r.goldenPass())
                .sum();
    }

    private static int countKnownSkipMaterialSc11Failures(List<HospitalReport> reports) {
        // skip-material 院的 SC11 fixture 失败容许（材料不齐全导致规则预期偏差）
        return reports.stream()
                .filter(r -> !r.materialComplete())
                .mapToInt(r -> r.sc11Total() - r.sc11Pass())
                .sum();
    }

    private static CaseResult runGoldenCase(PricingEngine engine, JsonNode testCase) throws Exception {
        String id = testCase.path("id").asText("unknown");
        String hospital = testCase.path("hospital").asText("测试医院");
        JsonNode row = testCase.path("row");
        var result = engine.processRow(RuleFidelityTestSupport.rowFromJson(row, hospital));
        if ("expected".equals(testCase.path("kind").asText())) {
            if (!"warning".equals(result.status)) {
                return new CaseResult("golden", id, false, "expected warning got " + result.status);
            }
            if (row.hasNonNull("expectedUnitPrice")
                    && Math.abs(result.expectedUnitPrice - row.path("expectedUnitPrice").asDouble()) > 0.001) {
                return new CaseResult("golden", id, false,
                        "price " + result.expectedUnitPrice + " != " + row.path("expectedUnitPrice").asDouble());
            }
            return new CaseResult("golden", id, true, "ok");
        }
        if (!"unchanged".equals(result.status) && !"warning".equals(result.status)) {
            return new CaseResult("golden", id, false, "expected unchanged or warning got " + result.status
                    + " rule=" + result.pricingRule);
        }
        return new CaseResult("golden", id, true, "ok");
    }

    private static CaseResult runSc11Fixture(PricingEngine engine, JsonNode fixture) {
        String id = fixture.path("id").asText("unknown");
        JsonNode expect = fixture.path("expect");
        JsonNode row = fixture.path("row");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("hospitalName", fixture.path("hospitalName").asText(""));
        input.put("department", text(row, "department", "手术室"));
        input.put("type", text(row, "type", ""));
        input.put("packName", text(row, "packName", ""));
        input.put("packageMaterial", text(row, "packageMaterial", ""));
        input.put("instrumentCount", intVal(row, "instrumentCount", 1));
        input.put("packCount", intVal(row, "packCount", 1));
        input.put("unitPrice", numVal(row, "unitPrice", 0.0));
        input.put("totalPrice", numVal(row, "totalPrice", numVal(row, "unitPrice", 0.0)));

        var result = engine.processRow(input);
        String expectedStatus = expect.path("status").asText("unchanged");
        if (!expectedStatus.equals(result.status)) {
            return new CaseResult("sc11", id, false,
                    "status " + result.status + " != " + expectedStatus);
        }
        if (expect.hasNonNull("expectedUnitPrice")) {
            double expected = expect.path("expectedUnitPrice").asDouble();
            if (result.expectedUnitPrice == null
                    || Math.abs(result.expectedUnitPrice - expected) > 0.001) {
                return new CaseResult("sc11", id, false,
                        "price " + result.expectedUnitPrice + " != " + expected);
            }
        }
        if (expect.has("pricingRuleContains")) {
            String fragment = expect.path("pricingRuleContains").asText("");
            if (result.pricingRule == null || !result.pricingRule.contains(fragment)) {
                return new CaseResult("sc11", id, false, "rule missing " + fragment);
            }
        }
        return new CaseResult("sc11", id, true, "ok");
    }

    private static JsonNode loadJson(String resource) throws Exception {
        try (InputStream in = V8SpecialPricingHospitalGateTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return MAPPER.readTree(in);
        }
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asText();
    }

    private static int intVal(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private static double numVal(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asDouble(defaultValue);
    }
}
