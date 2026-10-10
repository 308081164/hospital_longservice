package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkBillPriceRuleApplierTest {

    private final ClerkBillPriceRuleApplier applier = new ClerkBillPriceRuleApplier();
    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void matchesPaperPlasticDimensionToCmTierForSanjing() {
        ObjectNode compiled = compiler.compileForCustomer("SANJING-SB");
        BillRowItem row = new BillRowItem();
        row.setType("额外包(纸塑袋)");
        row.setPackName("盘-1/Z3032");
        row.setPackageMaterial("高温纸塑袋300*320");
        row.setInstrumentCount(1);
        row.setPackCount(1);
        row.setTotalPrice(30.0);

        var match = applier.findMatchingPriceRule(compiled, row, "SANJING-SB", java.util.Set.of());
        assertThat(match).isPresent();
        assertThat(match.get().rule().path("params").path("packagingMaterial").asText()).isEqualTo("25cm");
        assertThat(match.get().exportTotal()).isEqualTo(12.8);
    }

    @Test
    void matchesPaperPlasticDimensionToCmTierForSanjing20cm() {
        ObjectNode compiled = compiler.compileForCustomer("SANJING-SB");
        BillRowItem row = new BillRowItem();
        row.setType("额外包(纸塑袋)");
        row.setPackName("测试");
        row.setPackageMaterial("高温纸塑袋200*250");
        row.setInstrumentCount(1);
        row.setPackCount(1);
        row.setTotalPrice(30.0);

        var match = applier.findMatchingPriceRule(compiled, row, "SANJING-SB", java.util.Set.of());
        assertThat(match).isPresent();
        assertThat(match.get().rule().path("params").path("packagingMaterial").asText()).isEqualTo("20cm");
        assertThat(match.get().exportTotal()).isEqualTo(10.4);
    }

    @Test
    void appliesPerPiecePriceForDaowaiInstrumentPack() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("测试包");
        row.setInstrumentCount(5);
        row.setPackCount(1);
        row.setTotalPrice(100.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(15.0);
    }

    @Test
    void wildcardPackNameMatchesDressingRule() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("敷料包");
        row.setPackName("任意名称");
        row.setTotalPrice(30.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(19.0);
    }

    @Test
    void appliesPackNamePriceForObstetrics() {
        ObjectNode compiled = compiler.compileForCustomer("NG-FUCHAN");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("腹腔镜");
        row.setTotalPrice(200.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(170.5);
    }

    @Test
    void eryyNgBillDiscountRewritesRuleUnitPrice() {
        ObjectNode compiled = compiler.compileForCustomer("ERYY-NG");
        assertThat(compiled).isNotNull();
        BillRowItem row = new BillRowItem();
        row.setRowNumber(5);
        row.setPackName("测试包");
        row.setType("额外包(纸塑袋)");
        row.setPackageMaterial("高温纸塑袋100*200");
        row.setPackCount(1);
        row.setInstrumentCount(1);
        row.setExpectedUnitPrice(100.0);
        row.setUnitPrice(100.0);
        row.setTotalPrice(100.0);

        var priced = applier.apply(compiled, List.of(row));
        assertThat(priced.rows().get(0).getUnitPrice()).isEqualTo(100.0);
        assertThat(priced.validationWarnings()).isEmpty();

        var discounted = new com.hospital.backend.export.ExportStageDiscountApplier()
                .apply(compiled, priced.rows());
        assertThat(discounted.get(0).getExpectedUnitPrice()).isEqualTo(70.0);
        assertThat(discounted.get(0).getUnitPrice()).isEqualTo(70.0);
    }

    @Test
    void appliesTaipingDressingPriceRule() {
        ObjectNode compiled = compiler.compileForCustomer("TAIPING-RM");
        BillRowItem row = new BillRowItem();
        row.setType("敷料包");
        row.setPackName("敷料大");
        row.setTotalPrice(30.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(18.6);
    }

    @Test
    void appliesTaipingPaperPlasticSizeRule() {
        ObjectNode compiled = compiler.compileForCustomer("TAIPING-RM");
        BillRowItem row = new BillRowItem();
        row.setType("额外包(纸塑袋)");
        row.setPackageMaterial("纸塑袋20cm");
        row.setPackName("测试");
        row.setTotalPrice(20.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(6.83);
    }

    @Test
    void validatesSystemPriceWithoutChangingExportPrice() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("敷料包");
        row.setPackName("大敷料");
        row.setRowNumber(3);
        row.setTotalPrice(30.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(19.0);
        assertThat(result.validationWarnings()).isNotEmpty();
    }

    @Test
    void aolanPackNamePriceMatchesInstrumentPackTypeNotSterilizationLabel() {
        ObjectNode compiled = compiler.compileForCustomer("AOLAN-YY");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("骨科基础包-42件");
        row.setInstrumentCount(42);
        row.setPackCount(1);
        row.setTotalPrice(236.5);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(197.0);
    }

    @Test
    void aolanPackNamePriceHonorsInstrumentCountRange() {
        ObjectNode compiled = compiler.compileForCustomer("AOLAN-YY");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("整形包");
        row.setInstrumentCount(6);
        row.setPackCount(1);
        row.setTotalPrice(99.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(8.0);
    }

    @Test
    void aolanExactPackNameDoesNotMatchEmbeddedShorterKeyword() {
        ObjectNode compiled = compiler.compileForCustomer("AOLAN-YY");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("小缝合包门诊");
        row.setInstrumentCount(43);
        row.setPackCount(1);
        row.setTotalPrice(99.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(16.5);
    }

    @Test
    void packNameKeywordsUseExactTokenByDefault() {
        ObjectNode compiled = compiledWithPackNameRule("小缝合包", 157.0);
        BillRowItem embedded = new BillRowItem();
        embedded.setPackName("小缝合包门诊");
        embedded.setInstrumentCount(43);
        embedded.setTotalPrice(99.0);

        BillRowItem exact = new BillRowItem();
        exact.setPackName("小缝合包");
        exact.setInstrumentCount(43);
        exact.setTotalPrice(99.0);

        assertThat(applier.apply(compiled, List.of(embedded)).rows().get(0).getTotalPrice()).isEqualTo(99.0);
        assertThat(applier.apply(compiled, List.of(exact)).rows().get(0).getTotalPrice()).isEqualTo(157.0);
    }

    @Test
    void packNameKeywordsSupportContainsSuffix() {
        ObjectNode compiled = compiledWithPackNameRule("棉球@contains", 2.0);
        BillRowItem row = new BillRowItem();
        row.setPackName("敷料棉球-10个");
        row.setTotalPrice(30.0);

        assertThat(applier.apply(compiled, List.of(row)).rows().get(0).getTotalPrice()).isEqualTo(2.0);
    }

    private static ObjectNode compiledWithPackNameRule(String keyword, double price) {
        ObjectNode compiled = JsonUtils.getObjectMapper().createObjectNode();
        ArrayNode clerkRules = compiled.putArray("clerkRules");
        ObjectNode rule = JsonUtils.getObjectMapper().createObjectNode();
        rule.put("ruleType", "PACK_NAME_PRICE");
        rule.put("stage", "bill_export");
        rule.put("name", keyword);
        rule.put("isActive", true);
        rule.put("priority", 10);
        ObjectNode params = rule.putObject("params");
        ArrayNode keywords = params.putArray("packNameKeywords");
        keywords.add(keyword);
        params.put("unitPrice", price);
        params.put("unitPriceMode", "FIXED");
        clerkRules.add(rule);
        return compiled;
    }
}
