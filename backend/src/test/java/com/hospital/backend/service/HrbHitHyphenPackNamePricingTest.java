package com.hospital.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * 哈工大包名「数字前带短横线」与紧凑写法等价命中（针架-1针-6 ≈ 针架1针6）。
 */
class HrbHitHyphenPackNamePricingTest {

    private static final String HOSPITAL = "哈尔滨工业大学医院";

    private PricingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = PricingEngineTestSupport.engineForCustomerCode("HRB-HIT");
    }

    @Test
    void needleRackHyphenPackNameHitsFoldNotStandardTier() {
        PricingEngine.ProcessedResult hyphen = engine.processRow(row(
                "针架-1针-6/Z1020",
                "额外包（纸塑袋）",
                "高温纸塑袋100*200",
                6,
                1,
                16.5,
                16.5));
        PricingEngine.ProcessedResult compact = engine.processRow(row(
                "针架1针6/Z1020",
                "额外包（纸塑袋）",
                "高温纸塑袋100*200",
                6,
                1,
                16.5,
                16.5));

        assertThat(hyphen.expectedUnitPrice).isCloseTo(compact.expectedUnitPrice, offset(0.02));
        assertThat(hyphen.expectedUnitPrice).isCloseTo(16.5, offset(0.02));
        assertThat(hyphen.pricingRule).contains("针架");
    }

    @Test
    void dressingBowlHyphenAliasMatchesFixedPrice() {
        PricingEngine.ProcessedResult result = engine.processRow(row(
                "换药器-1/Z2032",
                "额外包（纸塑袋）",
                "高温纸塑袋150*260",
                1,
                1,
                11.0,
                11.0));
        assertThat(result.expectedUnitPrice).isCloseTo(13.0, offset(0.02));
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
}
