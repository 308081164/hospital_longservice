package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 市五院敷料规则须仅在「敷料包（无纺布）」类型生效，纸塑袋行即使包名含纱布也不得误命中。
 */
class HrbWyDressingPackTypeRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOSPITAL = "哈尔滨市第五医院";

    @Test
    void gauzeFixedPrice_appliesOnlyOnDressingPackType() throws Exception {
        PricingEngine engine = engineWithGauzeDressingRule();

        PricingEngine.ProcessedResult dressing = engine.processRow(row(
                "敷料包(无纺布包)",
                "外科纱布包",
                "无纺布-60×60-50g",
                1, 1, 15.0, 15.0));
        assertThat(dressing.expectedUnitPrice).isEqualTo(15.0);

        PricingEngine.ProcessedResult paperPlastic = engine.processRow(row(
                "额外包(纸塑袋)",
                "外科纱布包",
                "高温纸塑袋75*370",
                1, 1, 8.0, 8.0));
        assertThat(paperPlastic.expectedUnitPrice).isNotEqualTo(15.0);
    }

    private static PricingEngine engineWithGauzeDressingRule() throws Exception {
        ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        ObjectNode special = (ObjectNode) rules.get("specialRules");
        ObjectNode fixed = MAPPER.createObjectNode();
        fixed.put("price", 15.0);
        fixed.put("skipPackaging", true);
        fixed.put("skipDiscount", true);
        fixed.put("keywordMatchMode", "contains");
        fixed.set("keywords", MAPPER.createArrayNode().add("纱布"));
        fixed.set("acceptedTypes", MAPPER.createArrayNode().add("敷料包（无纺布）"));
        fixed.set("hospitals", MAPPER.createArrayNode().add(HOSPITAL));
        special.set("fixedPrices", MAPPER.createArrayNode().add(fixed));
        return new PricingEngine(rules);
    }

    private static java.util.Map<String, Object> row(
            String type, String packName, String material,
            int instrumentCount, int packCount,
            double unitPrice, double totalPrice) {
        return java.util.Map.of(
                "hospitalName", HOSPITAL,
                "department", "手术室",
                "type", type,
                "packName", packName,
                "packageMaterial", material,
                "instrumentCount", instrumentCount,
                "packCount", packCount,
                "unitPrice", unitPrice,
                "totalPrice", totalPrice);
    }
}
