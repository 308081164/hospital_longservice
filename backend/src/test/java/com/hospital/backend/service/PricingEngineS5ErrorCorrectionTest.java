package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5 纠错最小用例集 — 引擎级自动化（EC-PRICE / EC-PACK / EC-BILLING-OFF）。
 * 规格说明：{@code 测试用例/S5纠错测试最小用例集.md}
 */
class PricingEngineS5ErrorCorrectionTest {

    @Test
    @DisplayName("EC-PRICE · DAOWAI-RM 路径覆盖行账单价偏高 → warning")
    void ecPrice_wrongUnitPrice_yieldsWarning_daowaiLowTempPath() {
        ObjectNode rules = (ObjectNode) PricingEngineTestSupport.defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        ObjectNode pathOverride = billingProfile.putObject("pathOverride");
        pathOverride.put("disableLowTemp", true);
        pathOverride.put("forceHighTempUnitPrice", 3.0);

        PricingEngine engine = new PricingEngine(rules);
        // 4 件 × 3 元 = 12；故意账单 13.2（+10%）
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                "道外人民",
                "单包装包(老肯低温)",
                "普通器械-4/Z7526",
                "低温纸塑袋200*600",
                4,
                1,
                13.2,
                13.2));

        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(12.0);
        assertThat(result.difference).isNotEqualTo(0.0);
    }

    @Test
    @DisplayName("EC-PACK · HRB-NGJY 敷料包无包材 → warning（0 元导入默认价）")
    void ecPack_unrecognizedDressingPack_yieldsWarning_ngjy() {
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());
        Map<String, Object> row = PricingEngineTestSupport.row(
                "哈尔滨市南岗区人民医院（九院）",
                "敷料包",
                "敷料包",
                "",
                0,
                1,
                0,
                0);
        row.put("department", "手术室");
        PricingEngine.ProcessedResult result = engine.processRow(row);

        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(25.0);
        assertThat(result.difference).isEqualTo(25.0);
        assertThat(result.notes).anyMatch(n -> n.contains("0 元导入"));
    }

    @Test
    @DisplayName("EC-PACK · NG-FUCHAN 错包名导致期望价与账单不符 → warning")
    void ecPack_wrongPackName_yieldsWarning_ngFuchanStyleRow() {
        PricingEngine engine = new PricingEngine(PricingEngineTestSupport.defaultRules());
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "故意错名-不存在的器械包/Z9999",
                "高温纸塑袋75*200",
                4,
                1,
                99.0,
                99.0));

        assertThat(result.status).isEqualTo("warning");
        assertThat(result.difference).isNotEqualTo(0.0);
    }

    @Test
    @DisplayName("EC-BILLING-OFF · billingProfile.enabled=false → unchanged 保留原价")
    void ecBillingOff_keepsOriginalPrice_unchanged() {
        ObjectNode rules = (ObjectNode) PricingEngineTestSupport.defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", false);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                "南岗区妇产医院",
                "额外包(纸塑袋)",
                "普通器械-4/Z7526",
                "高温纸塑袋75*200",
                4,
                1,
                88.0,
                88.0));

        assertThat(result.status).isEqualTo("unchanged");
        assertThat(result.expectedUnitPrice).isEqualTo(88.0);
        assertThat(result.correctedTotalPrice).isEqualTo(88.0);
        assertThat(result.pricingRule).isEqualTo("特色账单已关闭");
        assertThat(result.notes).anyMatch(n -> n.contains("保留原始价格"));
    }

    @Test
    @DisplayName("EC-PRICE · 固定价行故意写错单价 → warning（省医院南岗/固定价通用）")
    void ecPrice_wrongUnitPrice_yieldsWarning_fixedPriceRow() {
        ObjectNode rules = (ObjectNode) PricingEngineTestSupport.defaultRules();
        ArrayNode fixedPrices = (ArrayNode) rules.path("specialRules").path("fixedPrices");
        ObjectNode fixed = fixedPrices.addObject();
        fixed.put("name", "航天风华挖勺每件 5.5 元");
        fixed.set("hospitals", new ObjectMapper().createArrayNode().add("哈尔滨航天风华医院"));
        fixed.set("keywords", new ObjectMapper().createArrayNode().add("挖勺"));
        fixed.put("price", 5.5);
        fixed.put("pricePerInstrument", true);
        fixed.put("skipPackaging", true);

        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨航天风华医院",
                "额外包(纸塑袋)",
                "挖勺-2/z7530",
                "高温纸塑袋75*300",
                8,
                4,
                13.5,
                54));

        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isEqualTo(11.0);
    }

    /**
     *  package-private helpers shared with S5 tests only (minimal copy from PricingEngineTest).
     */
    static final class PricingEngineTestSupport {
        private PricingEngineTestSupport() {
        }

        static Map<String, Object> row(
                String hospitalName,
                String type,
                String packName,
                String packageMaterial,
                int instrumentCount,
                int packCount,
                double unitPrice,
                double totalPrice
        ) {
            Map<String, Object> row = new HashMap<>();
            row.put("hospitalName", hospitalName);
            row.put("type", type);
            row.put("packName", packName);
            row.put("packageMaterial", packageMaterial);
            row.put("instrumentCount", instrumentCount);
            row.put("packCount", packCount);
            row.put("unitPrice", unitPrice);
            row.put("totalPrice", totalPrice);
            return row;
        }

        static JsonNode defaultRules() {
            Map<String, Object> rules = new LinkedHashMap<>(DefaultPricingTemplate.buildRulesMap());
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(rules);
        }
    }
}
