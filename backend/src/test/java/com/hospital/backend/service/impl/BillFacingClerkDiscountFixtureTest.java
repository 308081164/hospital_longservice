package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.export.ExportStageDiscountApplier;
import com.hospital.backend.service.ClerkBillPriceRuleApplier;
import com.hospital.backend.service.ClerkRuleCompiler;
import com.hospital.backend.service.PricingEngine;
import com.hospital.backend.service.PricingEngineTestSupport;
import com.hospital.backend.service.ReconciliationPricingOrchestrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 四家医院真实账单：规则单价应等于已折后的原单价。
 * 省二院七折、省医院六折。结款物流费不参与行单价。
 */
class BillFacingClerkDiscountFixtureTest {

    private static final int SAMPLE_LIMIT = 40;

    private static ReconciliationPricingOrchestrator orchestrator;
    private static ClerkRuleCompiler compiler;

    @BeforeAll
    static void setUp() {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        compiler = new ClerkRuleCompiler(new ClerkRuleIndex());
    }

    @Test
    void eryySongbeiDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture(
                "eryy-sb.xlsx",
                "ERYY-SB",
                "黑龙江省第二医院（松北区）",
                0.7,
                true);
    }

    @Test
    void eryyNangangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture(
                "eryy-ng.xlsx",
                "ERYY-NG",
                "黑龙江省第二医院（南岗院区）",
                0.7,
                false);
    }

    @Test
    void shengYyXiangfangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture(
                "sheng-yy-xf.xlsx",
                "SHENG-YY-XF",
                "黑龙江省医院（香坊院区）",
                0.6,
                true);
    }

    @Test
    void shengYyNangangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture(
                "sheng-yy-ng.xlsx",
                "SHENG-YY-NG",
                "黑龙江省医院（南岗院区）",
                0.6,
                true);
    }

    private void assertDiscountedFixture(
            String fileName,
            String customerCode,
            String hospitalName,
            double rate,
            boolean customerInManifest) throws Exception {
        List<Map<String, Object>> rows = parseFixture(fileName);
        assertThat(rows).as(fileName).isNotEmpty();
        PricingEngine engine = customerInManifest
                ? PricingEngineTestSupport.engineForCustomerCode(customerCode)
                : new PricingEngine(PricingEngineTestSupport.defaultRules());
        ObjectNode clerkCompiled = compiler.compileForCustomer(customerCode);
        assertThat(clerkCompiled).isNotNull();

        List<Map<String, Object>> sample = sampleRows(rows);
        int considered = 0;
        List<String> misses = new ArrayList<>();
        boolean sawScreenshotShape = false;
        for (Map<String, Object> row : sample) {
            row.put("hospitalName", hospitalName);
            Double billUnit = doubleVal(row.get("unitPrice"));
            if (billUnit == null || billUnit <= 0) {
                continue;
            }
            PricingEngine.ProcessedResult engineOnly = engine.processRow(row);
            if (engineOnly.expectedUnitPrice == null || engineOnly.expectedUnitPrice <= 0) {
                continue;
            }
            double discounted = round2(engineOnly.expectedUnitPrice * rate);
            if (Math.abs(billUnit - discounted) > 0.05) {
                continue;
            }
            considered++;
            PricingEngine.ProcessedResult result = orchestrator.processRow(
                    row, engine, clerkCompiled, customerCode, Set.of());
            if (result.expectedUnitPrice == null
                    || Math.abs(result.expectedUnitPrice - billUnit) > 0.05) {
                misses.add(String.format(
                        "%s bill=%.2f engine=%.2f rule=%s",
                        row.get("packName"),
                        billUnit,
                        engineOnly.expectedUnitPrice,
                        result.expectedUnitPrice));
                continue;
            }
            assertThat(result.expectedUnitPrice).isCloseTo(discounted, offset(0.05));
            assertThat(((Number) result.billingNotes.get("priceBeforeDiscount")).doubleValue())
                    .isCloseTo(engineOnly.expectedUnitPrice, offset(0.05));
            if (Math.abs(billUnit - 24.5) <= 0.02 && rate == 0.7
                    && String.valueOf(row.get("packName")).contains("呼吸器")) {
                sawScreenshotShape = true;
                assertThat(engineOnly.expectedUnitPrice).isCloseTo(35.0, offset(0.05));
                assertThat(result.expectedUnitPrice).isCloseTo(24.5, offset(0.05));
                assertThat(result.status).isEqualTo("unchanged");
            }
        }
        assertThat(considered)
                .as("%s rows whose bill already equals standard×%.2f", customerCode, rate)
                .isGreaterThanOrEqualTo(12);
        assertThat(misses).as(String.join("\n", misses)).isEmpty();
        if ("ERYY-SB".equals(customerCode)) {
            assertThat(sawScreenshotShape)
                    .as("fixture should contain 简易呼吸器 at 24.50")
                    .isTrue();
        }
    }

    private static List<Map<String, Object>> sampleRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> picked = new ArrayList<>();
        int step = Math.max(1, rows.size() / SAMPLE_LIMIT);
        for (int i = 0; i < rows.size() && picked.size() < SAMPLE_LIMIT; i += step) {
            picked.add(new java.util.LinkedHashMap<>(rows.get(i)));
        }
        for (Map<String, Object> row : rows) {
            String pack = String.valueOf(row.get("packName"));
            Double unit = doubleVal(row.get("unitPrice"));
            if (unit != null && Math.abs(unit - 24.5) <= 0.02 && pack.contains("呼吸器")) {
                picked.add(new java.util.LinkedHashMap<>(row));
                break;
            }
        }
        return picked;
    }

    private static List<Map<String, Object>> parseFixture(String fileName) throws Exception {
        Path path = fixturePath(fileName);
        try (InputStream in = Files.newInputStream(path)) {
            return ExcelBillImportSupport.parseWorkbookData(in).rows();
        }
    }

    private static Path fixturePath(String fileName) {
        List<Path> candidates = List.of(
                Path.of("/测试用例/fixtures", fileName),
                Path.of("测试用例/fixtures", fileName),
                Path.of("../测试用例/fixtures", fileName),
                Path.of("../../测试用例/fixtures", fileName));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到夹具 " + fileName + "，已查找 " + candidates);
    }

    private static Double doubleVal(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
