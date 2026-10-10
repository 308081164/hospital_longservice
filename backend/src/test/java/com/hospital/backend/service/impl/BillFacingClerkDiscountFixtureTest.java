package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.export.ExportStageDiscountApplier;
import com.hospital.backend.mapper.CustomerAliasMapper;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.service.ClerkBillPriceRuleApplier;
import com.hospital.backend.service.ClerkRuleCompiler;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.PricingEngine;
import com.hospital.backend.service.PricingEngineTestSupport;
import com.hospital.backend.service.ReconciliationHospitalNameResolver;
import com.hospital.backend.service.ReconciliationPricingOrchestrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 四家医院真实账单：医院名从工作簿 D9/文件名解析，不向编排器硬编码 customerCode。
 * 省二院七折、省医院六折。ERYY-NG 不在客户档案时仍走内勤回退。
 */
class BillFacingClerkDiscountFixtureTest {

    private static final int SAMPLE_LIMIT = 40;

    private static ReconciliationPricingOrchestrator orchestrator;
    private static ClerkRuleCompiler compiler;
    private static ClerkRuleIndex clerkRuleIndex;

    @BeforeAll
    static void setUp() {
        orchestrator = new ReconciliationPricingOrchestrator(
                new ClerkBillPriceRuleApplier(),
                new ExportStageDiscountApplier());
        clerkRuleIndex = new ClerkRuleIndex();
        compiler = new ClerkRuleCompiler(clerkRuleIndex);
    }

    @Test
    void eryySongbeiDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture("eryy-sb.xlsx", "省二院松北.xlsx", "ERYY-SB",
                "黑龙江省第二医院（松北区）", 0.7);
    }

    @Test
    void eryyNangangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture("eryy-ng.xlsx", "省二院南岗.xlsx", "ERYY-NG",
                "黑龙江省第二医院（南岗院区）", 0.7);
    }

    @Test
    void shengYyXiangfangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture("sheng-yy-xf.xlsx", "省医院香坊.xlsx", "SHENG-YY-XF",
                "黑龙江省医院（香坊院区）", 0.6);
    }

    @Test
    void shengYyNangangDiscountedBillMatchesRuleUnitPrice() throws Exception {
        assertDiscountedFixture("sheng-yy-ng.xlsx", "省医院南岗.xlsx", "SHENG-YY-NG",
                "黑龙江省医院（南岗院区）", 0.6);
    }

    private void assertDiscountedFixture(
            String fixtureFile,
            String uploadFileName,
            String expectedCode,
            String expectedHospitalName,
            double rate) throws Exception {
        ExcelBillImportSupport.WorkbookParseResult parsed = parseFixture(fixtureFile);
        assertThat(parsed.rows()).as(fixtureFile).isNotEmpty();
        assertThat(parsed.hospitalDisplayNames()).as(fixtureFile + " D9").isNotEmpty();

        ResolvedImport resolved = resolveLikeImport(uploadFileName, parsed);
        assertThat(resolved.hospitalName())
                .as("%s 导入医院名应来自工作簿，而不是测试写死的院区", uploadFileName)
                .isEqualTo(expectedHospitalName);
        assertThat(resolved.customerCode())
                .as("%s 客户编码应由导入路径解析", uploadFileName)
                .isEqualTo(expectedCode);
        if ("ERYY-NG".equals(expectedCode)) {
            assertThat(parsed.hospitalDisplayNames()).contains("黑龙江省第二医院（南岗区）");
            assertThat(resolved.customerPresent()).isFalse();
        }

        PricingEngine engine = engineLikeImport(resolved.customerCode());
        ObjectNode clerkCompiled = compiler.compileForCustomer(resolved.customerCode());
        assertThat(clerkCompiled).isNotNull();

        List<Map<String, Object>> sample = sampleRows(parsed.rows());
        int considered = 0;
        List<String> misses = new ArrayList<>();
        boolean sawScreenshotShape = false;
        for (Map<String, Object> row : sample) {
            row.put("hospitalName", resolved.hospitalName());
            Double billUnit = doubleVal(row.get("unitPrice"));
            if (billUnit == null || billUnit <= 0) {
                continue;
            }
            PricingEngine.ProcessedResult engineOnly = engine.processRow(copyRow(row));
            if (engineOnly.expectedUnitPrice == null || engineOnly.expectedUnitPrice <= 0) {
                continue;
            }
            double discounted = round2(engineOnly.expectedUnitPrice * rate);
            if (Math.abs(billUnit - discounted) > 0.05) {
                continue;
            }
            considered++;
            PricingEngine.ProcessedResult result = priceLikeImport(row, engine, clerkCompiled, resolved.customerCode());
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
                .as("%s rows whose bill already equals standard×%.2f", expectedCode, rate)
                .isGreaterThanOrEqualTo(12);
        assertThat(misses).as(String.join("\n", misses)).isEmpty();
        if ("ERYY-SB".equals(expectedCode)) {
            assertThat(sawScreenshotShape)
                    .as("fixture should contain 简易呼吸器 at 24.50")
                    .isTrue();
        }
        if ("ERYY-NG".equals(expectedCode)) {
            assertScreenshotRow(parsed.rows(), engine, clerkCompiled, resolved, "剪刀(小)", 5.60, 8.00);
            assertScreenshotRow(parsed.rows(), engine, clerkCompiled, resolved, "气枪头", 19.25, 27.50);
        }
    }

    /**
     * 与 {@code HospitalReconciliationServiceImpl.importAndProcess} /
     * {@code processRowWithOrchestrator} 相同：先解析医院名，客户档案未命中再用内勤 baseline。
     */
    private ResolvedImport resolveLikeImport(
            String uploadFileName,
            ExcelBillImportSupport.WorkbookParseResult parsed) {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        CustomerAliasMapper aliasMapper = mock(CustomerAliasMapper.class);
        List<Customer> customers = productionCustomersPresentInDb();
        when(customerMapper.selectAll()).thenReturn(customers);
        when(aliasMapper.selectAllActive()).thenReturn(List.of());
        CustomerResolver customerResolver = new CustomerResolver(customerMapper, aliasMapper);
        ReconciliationHospitalNameResolver nameResolver =
                new ReconciliationHospitalNameResolver(customerResolver, clerkRuleIndex);

        String hospitalNameParam = parsed.hospitalDisplayNames().isEmpty()
                ? ""
                : parsed.hospitalDisplayNames().get(0);
        String hospitalName = nameResolver.resolve(
                hospitalNameParam,
                uploadFileName,
                parsed.hospitalDisplayNames(),
                parsed.headerAreaTexts());
        Optional<Customer> customer = customerResolver.resolveByName(hospitalName);
        String customerCode = customer.map(Customer::getCode).orElse(null);
        if (customerCode == null) {
            customerCode = clerkRuleIndex.resolveCustomerCodeByHospitalName(hospitalName);
        }
        return new ResolvedImport(hospitalName, customerCode, customer.isPresent());
    }

    /** 本地库现状：ERYY-SB / 省医院两院区在档，ERYY-NG 已被严格清单清掉。 */
    private static List<Customer> productionCustomersPresentInDb() {
        return List.of(
                customer(47L, "ERYY-SB", "黑龙江省第二医院（松北区）"),
                customer(49L, "SHENG-YY-NG", "黑龙江省医院（南岗院区）"),
                customer(50L, "SHENG-YY-XF", "黑龙江省医院（香坊院区）"));
    }

    private static Customer customer(long id, String code, String canonicalName) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCode(code);
        customer.setCanonicalName(canonicalName);
        customer.setBillingEnabled(true);
        customer.setStatus("active");
        return customer;
    }

    private static PricingEngine engineLikeImport(String customerCode) throws Exception {
        try {
            return PricingEngineTestSupport.engineForCustomerCode(customerCode);
        } catch (IllegalArgumentException ignored) {
            return new PricingEngine(PricingEngineTestSupport.defaultRules());
        }
    }

    private PricingEngine.ProcessedResult priceLikeImport(
            Map<String, Object> row,
            PricingEngine engine,
            ObjectNode clerkCompiled,
            String customerCode) {
        return orchestrator.processRow(row, engine, clerkCompiled, customerCode, Set.of());
    }

    private void assertScreenshotRow(
            List<Map<String, Object>> rows,
            PricingEngine engine,
            ObjectNode clerkCompiled,
            ResolvedImport resolved,
            String packKeyword,
            double billUnit,
            double standardUnit) {
        Map<String, Object> row = rows.stream()
                .filter(item -> String.valueOf(item.get("packName")).contains(packKeyword))
                .filter(item -> {
                    Double unit = doubleVal(item.get("unitPrice"));
                    return unit != null && Math.abs(unit - billUnit) <= 0.02;
                })
                .findFirst()
                .map(BillFacingClerkDiscountFixtureTest::copyRow)
                .orElse(null);
        assertThat(row).as("screenshot row %s @ %.2f", packKeyword, billUnit).isNotNull();
        row.put("hospitalName", resolved.hospitalName());
        PricingEngine.ProcessedResult engineOnly = engine.processRow(copyRow(row));
        assertThat(engineOnly.expectedUnitPrice).isCloseTo(standardUnit, offset(0.05));
        PricingEngine.ProcessedResult result = priceLikeImport(row, engine, clerkCompiled, resolved.customerCode());
        assertThat(result.expectedUnitPrice).isCloseTo(billUnit, offset(0.05));
        assertThat(result.status).isEqualTo("unchanged");
    }

    private static List<Map<String, Object>> sampleRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> picked = new ArrayList<>();
        int step = Math.max(1, rows.size() / SAMPLE_LIMIT);
        for (int i = 0; i < rows.size() && picked.size() < SAMPLE_LIMIT; i += step) {
            picked.add(copyRow(rows.get(i)));
        }
        for (Map<String, Object> row : rows) {
            String pack = String.valueOf(row.get("packName"));
            Double unit = doubleVal(row.get("unitPrice"));
            if (unit != null && Math.abs(unit - 24.5) <= 0.02 && pack.contains("呼吸器")) {
                picked.add(copyRow(row));
                break;
            }
        }
        return picked;
    }

    private static Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private static ExcelBillImportSupport.WorkbookParseResult parseFixture(String fileName) throws Exception {
        Path path = fixturePath(fileName);
        try (InputStream in = Files.newInputStream(path)) {
            return ExcelBillImportSupport.parseWorkbookData(in);
        }
    }

    private static Path fixturePath(String fileName) {
        List<Path> candidates = List.of(
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

    private record ResolvedImport(String hospitalName, String customerCode, boolean customerPresent) {
    }
}
