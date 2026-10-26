package com.hospital.backend.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.service.ClerkRuleCompiler;
import com.hospital.backend.service.ClerkRuleExportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClerkSheetStyleTest {

    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void mergedLetterUsesSheetLabelsAndExistingFiftyYuanTrip() throws Exception {
        JsonNode sanfuBaseline = new ClerkRuleIndex().baselineForCustomer("SANFU-SQ");
        JsonNode xiangfangBaseline = new ClerkRuleIndex().baselineForCustomer("XIANGFANG-ZY");
        assertThat(ruleParam(sanfuBaseline, "LOGISTICS_FEE", "feePerTrip")).isEqualTo(50.0);
        assertThat(ruleParam(sanfuBaseline, "MERGED_SETTLEMENT", "sharedLogisticsFee")).isEqualTo(50.0);
        assertThat(ruleParam(xiangfangBaseline, "LOGISTICS_FEE", "feePerTrip")).isEqualTo(50.0);

        ObjectNode sanfu = compiler.compileForCustomer("SANFU-SQ");
        ObjectNode xiangfang = compiler.compileForCustomer("XIANGFANG-ZY");
        assertThat(sanfu.path("mergedSettlement").path("selfSide").asText()).isEqualTo("sanfu");
        assertThat(sanfu.path("mergedSettlement").path("mergeWith").asText()).isEqualTo("XIANGFANG-ZY");
        assertThat(sanfu.path("mergedSettlement").path("sharedLogisticsFee").asDouble()).isEqualTo(50.0);
        assertThat(xiangfang.path("mergedSettlement").path("selfSide").asText()).isEqualTo("xiangfang");
        assertThat(xiangfang.path("mergedSettlement").path("mergeWith").asText()).isEqualTo("SANFU-SQ");
        assertThat(ClerkSheetStyle.mergedLogisticsRemark(sanfu)).isEqualTo("50/次（当日合并计费）");
        assertThat(ClerkSheetStyle.mergedLogisticsRemark(xiangfang)).isEqualTo("50/次（当日合并计费）");

        List<ClerkSheetStyle.LetterLine> lines = ClerkSheetStyle.mergedLines(sanfu, 80, 40, 50);
        assertThat(lines).extracting(ClerkSheetStyle.LetterLine::remark)
                .containsExactly("三辅社区", "香坊中医院", "50/次（当日合并计费）");
        assertThat(ClerkSheetStyle.letterTotal(lines)).isEqualTo(170.0);

        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setHospitalName("哈尔滨市三辅社区医院");
        job.setSourceDateRange("2026-07-01至2026-07-31");
        job.setLogisticsFee(100.0);
        byte[] bytes = ClerkSheetStyleWorkbook.mergedLetter(job, sanfu, 80, 40, 100);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("三辅社区服务中心、哈尔滨市香坊区中医院7月份结款通知函");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("序号");
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("备注");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("三辅社区");
            assertThat(sheet.getRow(5).getCell(3).getStringCellValue()).isEqualTo("香坊中医院");
            assertThat(sheet.getRow(6).getCell(2).getNumericCellValue()).isEqualTo(100.0);
            assertThat(sheet.getRow(7).getCell(2).getNumericCellValue()).isEqualTo(220.0);
        }
    }

    @Test
    void guoyaoGridUsesExistingFormulaAndOmitsOperatingRoomOnCampus3() {
        JsonNode mainBaseline = new ClerkRuleIndex().baselineForCustomer("GUOYAO-MAIN");
        JsonNode thirdBaseline = new ClerkRuleIndex().baselineForCustomer("GUOYAO-3");
        assertThat(ruleParam(mainBaseline, "GUOYAO_SETTLEMENT_FORMULA", "instrumentPackDivisor")).isEqualTo(5.5);
        assertThat(ruleParam(mainBaseline, "GUOYAO_SETTLEMENT_FORMULA", "allocationDivisor")).isEqualTo(15.0);
        assertThat(ruleParam(thirdBaseline, "GUOYAO_SETTLEMENT_FORMULA", "instrumentPackDivisor")).isEqualTo(5.5);

        ObjectNode main = compiler.compileForCustomer("GUOYAO-MAIN");
        ObjectNode third = compiler.compileForCustomer("GUOYAO-3");
        assertThat(main.path("guoyaoSettlement").path("campus").asText()).isEqualTo("main");
        assertThat(third.path("guoyaoSettlement").path("campus").asText()).isEqualTo("campus3");

        HospitalReconciliationRow orPack = row("手术室", "器械包", "手术包", 6143.5, 0);
        HospitalReconciliationRow extra = row("手术室", "额外包", "额外包（纸塑袋）", 399, 25);
        HospitalReconciliationRow other = row("骨科", "器械包", "普通包", 830.5, 0);
        List<ClerkSheetStyle.GuoyaoLine> lines = ClerkSheetStyle.guoyaoLines(main, List.of(orPack, extra, other));
        ClerkSheetStyle.GuoyaoLine orLine = line(lines, "手术室器械包");
        assertThat(orLine.handles()).isEqualTo(1117);
        assertThat(orLine.accounted()).isEqualTo(747);
        assertThat(orLine.billedAmount()).isEqualTo(4108.5);
        ClerkSheetStyle.GuoyaoLine extraLine = line(lines, "手术室额外包");
        assertThat(extraLine.handles()).isNull();
        assertThat(extraLine.accounted()).isEqualTo(25);
        assertThat(extraLine.billedAmount()).isEqualTo(399.0);
        assertThat(lines).extracting(ClerkSheetStyle.GuoyaoLine::category).contains("手术室器械包", "其他科室敷料包");

        HospitalReconciliationRow campus3Or = row("手术室", "器械包", "手术包", 115 * 5.5, 0);
        List<ClerkSheetStyle.GuoyaoLine> campus3 = ClerkSheetStyle.guoyaoLines(third, List.of(campus3Or));
        assertThat(campus3).extracting(ClerkSheetStyle.GuoyaoLine::category)
                .contains("其余科室器械", "其余科室额外包", "敷料包")
                .doesNotContain("手术室器械包");
    }

    @Test
    void zy3LetterKeepsSevenFoldAndDoesNotDiscountLogistics() {
        JsonNode baseline = new ClerkRuleIndex().baselineForCustomer("ZY3-DIANLI");
        assertThat(ruleParam(baseline, "EXPORT_LAYOUT", "discountRate")).isEqualTo(0.7);
        ObjectNode compiled = compiler.compileForCustomer("ZY3-DIANLI");
        assertThat(ClerkSheetStyle.discountColumnRate(compiled)).isEqualTo(0.7);
        List<ClerkSheetStyle.LetterLine> lines =
                ClerkSheetStyle.discountColumnLines(compiled, 24562.50, 572, 1000);
        assertThat(lines).extracting(ClerkSheetStyle.LetterLine::index).contains("1", "2", "4");
        assertThat(amountOf(lines, "灭菌费用")).isEqualTo(24562.50);
        assertThat(discountOf(lines, "灭菌费用")).isEqualTo(17193.75);
        assertThat(discountOf(lines, "外来器械")).isEqualTo(400.40);
        assertThat(amountOf(lines, "物流费用")).isEqualTo(1000.0);
        assertThat(discountOf(lines, "物流费用")).isNull();
        assertThat(ClerkSheetStyle.discountColumnTotal(lines)).isEqualTo(18594.15);
    }

    @Test
    void zuyanDeptPriceSplitsExistingLogisticsBySterilizeRatio() {
        JsonNode baseline = new ClerkRuleIndex().baselineForCustomer("ZUYAN-NG");
        assertThat(ruleParam(baseline, "LOGISTICS_FEE", "feePerTrip")).isEqualTo(50.0);
        HospitalReconciliationRow gynecology = row("妇科", "器械包", "包", 100, 1);
        HospitalReconciliationRow surgery = row("外科", "器械包", "包", 300, 1);
        List<ClerkSheetStyle.DeptPriceLine> lines = ClerkSheetStyle.zuyanDeptLines(List.of(gynecology, surgery), 40);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).logisticsAmount()).isEqualTo(10.0);
        assertThat(lines.get(0).totalAmount()).isEqualTo(110.0);
        assertThat(lines.get(1).logisticsAmount()).isEqualTo(30.0);
        assertThat(lines.get(1).totalAmount()).isEqualTo(330.0);
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSourceDateRange("2026-08-01至2026-08-31");
        assertThat(ClerkSheetStyle.zuyanTitle(job)).isEqualTo("祖研-黑龙江省中医医院（南岗院区）2026年8月科室灭菌价格汇总");
    }

    @Test
    void styleNameFallbackDoesNotOpenUnrelatedHospitals() {
        assertThat(ClerkRuleExportService.settlementStyleCode("香坊中医院")).isEqualTo("XIANGFANG-ZY");
        assertThat(ClerkRuleExportService.settlementStyleCode("三辅社区服务中心")).isEqualTo("SANFU-SQ");
        assertThat(ClerkRuleExportService.settlementStyleCode("祖研-黑龙江省中医医院（三辅院区）")).isNull();
        assertThat(ClerkRuleExportService.settlementStyleCode("祖研-黑龙江省中医医院（南岗院区）")).isEqualTo("ZUYAN-NG");
        assertThat(ClerkRuleExportService.settlementStyleCode("黑龙江省医院（香坊院区）")).isNull();
        assertThat(ClerkRuleExportService.settlementStyleCode("国药总医院第三院区")).isEqualTo("GUOYAO-3");
        assertThat(ClerkRuleExportService.settlementStyleCode("黑龙江维多利亚妇产医院")).isNull();
        assertThat(MergedSettlementPartnerLookup.matchesPartner("SANFU-SQ", "祖研-黑龙江省中医医院（三辅院区）")).isFalse();
        assertThat(MergedSettlementPartnerLookup.matchesPartner("XIANGFANG-ZY", "香坊中医院")).isTrue();

        var resolver = mock(com.hospital.backend.service.CustomerResolver.class);
        when(resolver.resolveByName("香坊中医院")).thenReturn(java.util.Optional.empty());
        when(resolver.resolveByName("黑龙江维多利亚妇产医院")).thenReturn(java.util.Optional.empty());
        ClerkRuleExportService service = new ClerkRuleExportService(
                compiler, null, null, null, null, null, null, null, new ClerkRuleIndex(), resolver);
        var empty = com.hospital.backend.common.JsonUtils.getObjectMapper().createObjectNode();
        assertThat(service.mergeCompiledForSettlement("香坊中医院", empty).has("mergedSettlement")).isTrue();
        assertThat(service.mergeCompiledForSettlement("黑龙江维多利亚妇产医院", empty)
                .has("mergedSettlement")).isFalse();
    }

    private static HospitalReconciliationRow row(
            String sheet, String type, String pack, double amount, int instruments) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setSheetName(sheet);
        row.setType(type);
        row.setPackName(pack);
        row.setCorrectedTotalPrice(amount);
        row.setInstrumentCount(instruments);
        row.setStatus("unchanged");
        return row;
    }

    private static ClerkSheetStyle.GuoyaoLine line(List<ClerkSheetStyle.GuoyaoLine> lines, String category) {
        return lines.stream().filter(line -> category.equals(line.category())).findFirst().orElseThrow();
    }

    private static Double amountOf(List<ClerkSheetStyle.LetterLine> lines, String item) {
        return lines.stream().filter(line -> item.equals(line.item())).findFirst().orElseThrow().amount();
    }

    private static Double discountOf(List<ClerkSheetStyle.LetterLine> lines, String item) {
        return lines.stream().filter(line -> item.equals(line.item())).findFirst().orElseThrow().discountedAmount();
    }

    private static double ruleParam(JsonNode baseline, String ruleType, String field) {
        for (JsonNode rule : baseline.path("rules")) {
            if (ruleType.equals(rule.path("ruleType").asText()) && rule.path("params").has(field)) {
                return rule.path("params").path(field).asDouble();
            }
        }
        throw new AssertionError("missing " + ruleType + " " + field);
    }
}
