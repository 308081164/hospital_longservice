package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PricingEngineStandardPathTest {

    @Test
    void lowTempSinglePieceUsesStandardTier() throws Exception {
        PricingEngine engine = new PricingEngine(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap())
        );
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨工业大学医院",
                "department", "手术室",
                "type", "单包装包(老肯低温)",
                "packName", "普通器械-1/Z7526",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0
        ));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.expectedUnitPrice).isGreaterThan(0);
    }

    @Test
    void lowTempFivePieceUsesHigherTier() throws Exception {
        PricingEngine engine = new PricingEngine(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap())
        );
        PricingEngine.ProcessedResult one = engine.processRow(Map.of(
                "hospitalName", "哈尔滨工业大学医院",
                "type", "单包装包(老肯低温)",
                "packName", "普通器械-1/Z7526",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0
        ));
        PricingEngine.ProcessedResult five = engine.processRow(Map.of(
                "hospitalName", "哈尔滨工业大学医院",
                "type", "单包装包(老肯低温)",
                "packName", "普通器械-5/Z7526",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 5,
                "packCount", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0
        ));
        assertThat(five.expectedUnitPrice).isGreaterThan(one.expectedUnitPrice);
    }

    @Test
    void dressingPackUnderTwentyCmUsesGenericFixedPrice() throws Exception {
        PricingEngine engine = new PricingEngine(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap())
        );
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨市第二医院",
                "department", "手术室",
                "type", "敷料包(纸塑袋)",
                "packName", "棉球",
                "packageMaterial", "高温纸塑袋150*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 4.0,
                "totalPrice", 4.0
        ));
        assertThat(result.expectedUnitPrice).isCloseTo(2.5, within(0.05));
        assertThat(result.pricingRule).contains("棉球");
        assertThat(result.pricingPath).isEqualTo("standard");
    }
}
