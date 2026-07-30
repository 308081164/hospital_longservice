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

/**
 * 验证显式 billingMode 与 legacy 推断路径产出相同计价结果。
 */
class BillingModeMigrationParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectNode rules;

    @BeforeEach
    void setUp() {
        rules = (ObjectNode) MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "standard");
    }

    @Test
    void legacyAndExplicitPerPackProduceSamePrice() {
        ObjectNode legacyRules = rules.deepCopy();
        addFixedPriceRule(legacyRules, legacyRule("换药包legacy", 21.99, false, new String[] {"换药包"}));

        ObjectNode explicitRules = rules.deepCopy();
        addFixedPriceRule(explicitRules, explicitRule("换药包explicit", 21.99, "PER_PACK", new String[] {"换药包"}));

        Map<String, Object> row = row("黑龙江中医药大学附属第一医院", "器械包(ZSD)", "换药包", 1, 1, 0, 0);
        var legacy = new PricingEngine(legacyRules).processRow(row);
        var explicit = new PricingEngine(explicitRules).processRow(row);
        assertThat(explicit.expectedUnitPrice).isEqualTo(legacy.expectedUnitPrice).isEqualTo(21.99);
    }

    @Test
    void legacyAndExplicitPackNameSuffixProduceSamePrice() {
        ObjectNode legacyRules = rules.deepCopy();
        addFixedPriceRule(legacyRules, legacyRule("刮勺探针legacy", 5.5, true, new String[] {"刮勺探针"}));

        ObjectNode explicitRules = rules.deepCopy();
        addFixedPriceRule(explicitRules, explicitRule("刮勺探针explicit", 5.5, "PACK_NAME_SUFFIX", new String[] {"刮勺探针"}));

        Map<String, Object> row = row("附二南岗", "额外包(纸塑袋)", "刮勺探针4/z1035", 1, 1, 8, 8);
        var legacy = new PricingEngine(legacyRules).processRow(row);
        var explicit = new PricingEngine(explicitRules).processRow(row);
        assertThat(explicit.expectedUnitPrice).isEqualTo(legacy.expectedUnitPrice).isEqualTo(22.0);
    }

    @Test
    void goldenStylePerInstrumentMultiPackStillMatches() {
        addFixedPriceRule(rules, explicitRule("挖勺按件", 5.5, "PER_INSTRUMENT", new String[] {"挖勺"}));
        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> row = row(
                "哈尔滨航天风华医院", "额外包(纸塑袋)", "挖勺-2/z7530", 4, 8, 13.5, 54.0);
        var result = engine.processRow(row);
        assertThat(result.expectedUnitPrice).isEqualTo(11.0);
        assertThat(result.correctedTotalPrice).isEqualTo(44.0);
    }

    private void addFixedPriceRule(ObjectNode targetRules, ObjectNode rule) {
        ArrayNode fixedPrices = (ArrayNode) targetRules.path("specialRules").path("fixedPrices");
        fixedPrices.add(rule);
    }

    private ObjectNode legacyRule(String name, double price, boolean perInstrument, String[] keywords) {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("name", name);
        rule.put("price", price);
        if (perInstrument) {
            rule.put("pricePerInstrument", true);
        }
        if (keywords != null) {
            ArrayNode kws = rule.putArray("keywords");
            for (String kw : keywords) {
                kws.add(kw);
            }
        }
        return rule;
    }

    private ObjectNode explicitRule(String name, double price, String billingMode, String[] keywords) {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("name", name);
        rule.put("price", price);
        rule.put("billingMode", billingMode);
        if (!"PER_PACK".equals(billingMode)) {
            rule.put("pricePerInstrument", true);
        }
        if ("PACK_NAME_SUFFIX".equals(billingMode)) {
            rule.put("pieceCountSource", "PACK_NAME_LAST_NUMBER");
        }
        if (keywords != null) {
            ArrayNode kws = rule.putArray("keywords");
            for (String kw : keywords) {
                kws.add(kw);
            }
        }
        return rule;
    }

    private Map<String, Object> row(String hospital, String type, String packName,
                                    int packCount, int instrumentCount, double unitPrice, double totalPrice) {
        Map<String, Object> row = new HashMap<>();
        row.put("hospitalName", hospital);
        row.put("type", type);
        row.put("packName", packName);
        row.put("packageMaterial", "高温纸塑袋75*200");
        row.put("packCount", packCount);
        row.put("instrumentCount", instrumentCount);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }
}
