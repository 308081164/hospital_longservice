package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel「通用特殊收费」8 项 5合1 FOLD（SC11-T04/T05）验收：克氏针等全局规则。
 */
class GenericSmallItemFoldPricingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TYPE = "额外包(纸塑袋)";
    private static final String MATERIAL_10CM = "高温纸塑袋75*370";
    private static final String MATERIAL_30CM = "高温纸塑袋75*300";

    @Test
    void screenshotCase_kirschnerTwelve_matchesBill16_5_not66() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", "克氏针-12/Z7530", MATERIAL_30CM, 12, 16.5, 16.5));

        assertThat(result.expectedUnitPrice)
                .as("12 件应 ceil(12/5)×5.5=16.5，而非 12×5.5=66")
                .isEqualTo(16.5);
        assertThat(result.expectedUnitPrice).isNotEqualTo(66.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).isEqualTo("通用小件5合1免包材");
        assertThat(result.pricingRule).doesNotContain("高温纸塑袋10cm计费");
        assertThat(result.notes).anyMatch(n -> n.contains("通用小件5合1免包材") && n.contains("折算为 3 件"));
        assertThat(result.notes).noneMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
        assertThat(result.notes).noneMatch(n -> n.contains("大于等于 3 件，按 5.50 元/件 × 12"));
    }

    @Test
    void screenshotCase_kirschnerSixCoarse_matchesBill13_5_withBag() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", "克氏针(粗)-6/Z7530", MATERIAL_10CM, 6, 13.5, 13.5));

        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).isEqualTo("通用小件5合1含包材");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 2 件"));
    }

    @Test
    void guoyao2Hybrid_kirschnerTwelve_matchesBill16_5() throws Exception {
        PricingEngine engine = guoyao2HybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "国药总医院第二院区", "克氏针-12/Z7530", MATERIAL_30CM, 12, 16.5, 16.5));

        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.pricingRule).isEqualTo("通用小件5合1免包材");
        assertThat(result.notes).noneMatch(n -> n.contains("混合模式未命中特色规则，走标准灭菌计价"));
    }

    @Test
    void boundaryTen_withBag_foldTwoPiecesPlusPackaging() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", "克氏针-10/Z7530", MATERIAL_10CM, 10, 13.5, 13.5));

        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.pricingRule).isEqualTo("通用小件5合1含包材");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 2 件"));
    }

    @Test
    void boundaryEleven_noBag_foldThreePiecesOnly() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", "克氏针-11/Z7530", MATERIAL_10CM, 11, 16.5, 16.5));

        assertThat(result.expectedUnitPrice).isEqualTo(16.5);
        assertThat(result.pricingRule).isEqualTo("通用小件5合1免包材");
        assertThat(result.notes).anyMatch(n -> n.contains("折算为 3 件"));
    }

    @Test
    void kirschnerWirePliers_doesNotMatchGenericKirschnerFold() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", "克氏针折弯钳-1/W6050", MATERIAL_10CM, 1, 8.0, 8.0));

        assertThat(result.notes).noneMatch(n -> n.contains("通用小件5合1"));
    }

    @ParameterizedTest(name = "{0} 6件含包材 FOLD")
    @CsvSource({
            "银质针-6/Z7530",
            "卷棉子-6/Z7530",
            "内热针-6/Z7530",
    })
    void otherGenericKeywords_sixPieces_foldWithBag(String packName) throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "测试医院", packName, MATERIAL_10CM, 6, 13.5, 13.5));

        assertThat(result.expectedUnitPrice).isEqualTo(13.5);
        assertThat(result.pricingRule).contains("通用小件5合1含包材");
    }

    private static PricingEngine defaultHybridEngine() throws Exception {
        ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        return new PricingEngine(rules);
    }

    private static PricingEngine guoyao2HybridEngine() throws Exception {
        return new PricingEngine(RuleFidelityTestSupport.compileForCustomerCode("GUOYAO-2"));
    }

    private static Map<String, Object> row(
            String hospital, String packName, String material,
            int instrumentCount, double unitPrice, double totalPrice) {
        return Map.of(
                "hospitalName", hospital,
                "department", "手术室",
                "type", TYPE,
                "packName", packName,
                "packageMaterial", material,
                "instrumentCount", instrumentCount,
                "packCount", 1,
                "unitPrice", unitPrice,
                "totalPrice", totalPrice
        );
    }
}
