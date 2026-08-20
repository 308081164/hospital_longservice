package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 博尚（BOSHANG-YY）hybrid 模式下通用双层袋 35 元 + FIXED 规则优先回归。
 */
class BoshangDoubleBagLowTempPricingTest {

    @Test
    void boshangDoubleBagLensPricesAt35() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BOSHANG-YY");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "博尚医院",
                "department", "手术室",
                "type", "额外包(低温等离子)",
                "packName", "30°C镜头(粗)-1/双/Z1555",
                "packageMaterial", "低温纸塑袋 200*600",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 47.5,
                "totalPrice", 47.5
        ));
        assertThat(result.expectedUnitPrice).isEqualTo(35.0);
        assertThat(result.pricingRule).contains("双");
        assertThat(result.notes).anyMatch(note -> note.contains("35"));
    }

    @Test
    void boshangRotatorFixedPriceStillWinsOverStandardPath() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("BOSHANG-YY");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "博尚医院",
                "department", "手术室",
                "type", "额外包低温等离子",
                "packName", "旋切器1胶帽4/Z2045",
                "packageMaterial", "低温纸塑袋200*600",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 44.0,
                "totalPrice", 44.0
        ));
        assertThat(result.expectedUnitPrice).isEqualTo(44.0);
        assertThat(result.pricingRule).contains("博尚旋切器44");
    }
}
