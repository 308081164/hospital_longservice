package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 市五院纸塑袋误告警 + 车针小件折算回归（2026-09）。
 */
class ShiwuPaperPlasticPricingRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOSPITAL = "哈尔滨市第五医院";
    private static final String TYPE = "额外包(纸塑袋)";

    @Test
    void kaiKouQi_fourPieces_diffZero_noPackagingWarningWhenModuleEnabled() throws Exception {
        PricingEngine engine = hybridEngineWithPackagingModuleEnabled();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "开口器4件/Z1526", "高温纸塑袋 150*260", 4, 1, 22.0, 22.0));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.difference).isNotNull();
        assertThat(Math.abs(result.difference)).isLessThan(0.001);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).noneMatch(n -> n.contains("未配置具体选项价格"));
        assertThat(result.notes).anyMatch(n -> n.contains("不再计袋费"));
    }

    @Test
    void diSuShouJi_onePiece_diffZero_noPackagingWarningWhenModuleEnabled() throws Exception {
        PricingEngine engine = hybridEngineWithPackagingModuleEnabled();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "低速手机-1/Z7520", "高温纸塑袋75*370", 1, 1, 8.0, 8.0));

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).noneMatch(n -> n.contains("未配置具体选项价格"));
    }

    @Test
    void cheZhenFivePerPack_multiPack_appliesSmallItemFold_notRaw55Times5() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "车针-5件/(同颜色一包)Z7520", "高温纸塑袋75*370", 25, 5, 8.0, 40.0));

        assertThat(result.expectedUnitPrice)
                .as("5件/包应小件折算为1件+10cm袋费=8，而非5×5.5=27.5")
                .isEqualTo(8.0);
        assertThat(result.expectedUnitPrice).isNotEqualTo(27.5);
        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.notes).anyMatch(n -> n.contains("通用小件5合1含包材") || n.contains("名称含小件关键词"));
    }

    @Test
    void cheZhenFiveSinglePack_stillUsesSmallItemFold() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "车针-5/Z7520", "高温纸塑袋75*370", 5, 1, 8.0, 8.0));

        assertThat(result.expectedUnitPrice).isEqualTo(8.0);
        assertThat(result.status).isEqualTo("unchanged");
    }

    @Test
    void regularInstrument_fourPieces_stillUsesPerPiece55Times4() throws Exception {
        PricingEngine engine = defaultHybridEngine();
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "开口器4件/Z1526", "高温纸塑袋 150*260", 4, 1, 22.0, 22.0));

        assertThat(result.expectedUnitPrice).isEqualTo(22.0);
        assertThat(result.notes).anyMatch(n -> n.contains("大于等于 3 件，按 5.50 元/件 × 4"));
    }

    private static PricingEngine defaultHybridEngine() throws Exception {
        ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        return new PricingEngine(rules);
    }

    private static PricingEngine hybridEngineWithPackagingModuleEnabled() throws Exception {
        ObjectNode rules = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        rules.putObject("billingProfile").put("enabled", true).put("pricingMode", "hybrid");
        ObjectNode packaging = (ObjectNode) rules.get("packaging");
        packaging.put("enabled", true);
        ArrayNode items = MAPPER.createArrayNode();
        ObjectNode paperItem = MAPPER.createObjectNode();
        paperItem.put("name", "纸塑袋");
        paperItem.put("chargePerPack", true);
        paperItem.set("keywords", MAPPER.createArrayNode().add("纸塑袋"));
        paperItem.set("options", MAPPER.createArrayNode());
        items.add(paperItem);
        packaging.set("items", items);
        return new PricingEngine(rules);
    }

    private static Map<String, Object> row(
            String packName, String material,
            int instrumentCount, int packCount,
            double unitPrice, double totalPrice) {
        return Map.of(
                "hospitalName", HOSPITAL,
                "department", "ICU",
                "type", TYPE,
                "packName", packName,
                "packageMaterial", material,
                "instrumentCount", instrumentCount,
                "packCount", packCount,
                "unitPrice", unitPrice,
                "totalPrice", totalPrice);
    }
}
