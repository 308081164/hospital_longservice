package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 哈工大 HRB-HIT 特色计价回归（2026-08 账单价确认）。
 */
class HrbHitPricingRegressionTest {

    private static final String HOSPITAL = "哈尔滨工业大学医院";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PricingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PricingEngine(hrbHitPricingRules());
    }

    @Test
    void dressingBowlOnePieceShouldBe13() {
        assertExpectedPrice(
                row("换药碗-1/z2032", "额外包(纸塑袋)", "高温纸塑袋150*260", 1, 1, 11.0, 11.0),
                13.0,
                "warning");
    }

    @Test
    void dressingBowlTwoPiecesShouldBe16_5() {
        assertExpectedPrice(
                row("换药碗-2/z2032", "额外包(纸塑袋)", "高温纸塑袋150*260", 2, 1, 16.5, 16.5),
                16.5,
                "unchanged");
    }

    @Test
    void plantingBoxShouldBill24PiecesAt5_5() {
        assertExpectedPrice(
                row("种植盒-23件 盒1/w6050", "器械包(ZSD)", "", 24, 1, 132.0, 132.0),
                132.0,
                "unchanged");
    }

    @Test
    void cottonBallWithoutThreadShouldBe2_5() {
        assertExpectedPrice(
                row("棉球（不带线）", "敷料包(纸塑袋)", "高温纸塑袋150*260", 0, 30, 2.5, 75.0),
                2.5,
                "unchanged");
    }

    @Test
    void gallbladderPack10ShouldBe165() {
        assertExpectedPrice(
                row("胆囊包10件（盒1）", "额外包(纸塑袋)", "高温纸塑袋200*440", 10, 1, 300.0, 300.0),
                165.0,
                "warning");
    }

    private void assertExpectedPrice(Map<String, Object> row, double expectedUnit, String status) {
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.status).isEqualTo(status);
        assertThat(result.expectedUnitPrice).isCloseTo(expectedUnit, offset(0.02));
    }

    private static Map<String, Object> row(
            String packName,
            String type,
            String material,
            int instrumentCount,
            int packCount,
            double unitPrice,
            double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", HOSPITAL);
        row.put("packName", packName);
        row.put("type", type);
        row.put("packageMaterial", material);
        row.put("instrumentCount", instrumentCount);
        row.put("packCount", packCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }

    private static JsonNode hrbHitPricingRules() {
        ObjectNode rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "special_only");
        ObjectNode specialRules = (ObjectNode) rules.path("specialRules");
        ArrayNode fixedPrices = specialRules.withArray("fixedPrices");

        addFixedRule(fixedPrices, "哈工大换药碗-1 13", new String[] {"换药碗-1"}, 13.0, false);
        addFixedRule(fixedPrices, "哈工大换药碗-2 16.5", new String[] {"换药碗-2"}, 16.5, false);
        addFixedRule(fixedPrices, "哈工大种植盒23件5.5/件", new String[] {"种植盒-23件"}, 5.5, true);
        addFixedRule(fixedPrices, "哈工大棉球不带线2.5", new String[] {"棉球（不带线）", "棉球(不带线)"}, 2.5, false);
        addFixedRule(fixedPrices, "哈工大胆囊包10件165", new String[] {"胆囊包10件"}, 165.0, false);

        return rules;
    }

    private static void addFixedRule(
            ArrayNode fixedPrices,
            String name,
            String[] keywords,
            double price,
            boolean perInstrument) {
        ObjectNode rule = fixedPrices.insertObject(0);
        rule.put("name", name);
        rule.putArray("hospitals").add(HOSPITAL);
        ArrayNode kw = rule.putArray("keywords");
        for (String keyword : keywords) {
            kw.add(keyword);
        }
        rule.put("price", price);
        rule.put("skipPackaging", true);
        rule.put("skipHospitalDiscount", true);
        if (perInstrument) {
            rule.put("pricePerInstrument", true);
            rule.put("billingMode", "PER_INSTRUMENT");
        }
    }
}
