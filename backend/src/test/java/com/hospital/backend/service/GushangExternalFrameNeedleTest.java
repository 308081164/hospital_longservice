package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨伤科外架钢针 >6（Excel 241）：件数/6 进一 ×5.5 + 内层纸塑袋 + 0.3。
 * 内层 = 两张袋中宽度较小的那张。
 */
class GushangExternalFrameNeedleTest {

    private static final String HOSPITAL = "哈尔滨市骨伤科医院";
    private static final String DOUBLE_BAG = "高温纸塑袋250*250+高温纸塑袋200*250";

    private PricingEngine.ProcessedResult price(String packName, String material, int count) throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        return engine.processRow(PricingEngineTestSupport.row(
                HOSPITAL, "额外包（纸塑袋）", packName, material, count, 1, 60.0, 60.0));
    }

    @Test
    void overSixFoldsBySixPlusInnerBagPlusThirtyCents() throws Exception {
        var r = price("外架钢针-7件/双", DOUBLE_BAG, 7);
        // ceil(7/6)=2 → 11.0；再加内层袋费与 0.3
        assertThat(r.expectedUnitPrice).isGreaterThan(11.3);
        assertThat(r.expectedUnitPrice).isLessThan(25.0);
        assertThat(r.pricingRule).contains("外架钢针>6");
    }

    @Test
    void sixOrLessKeepsFixedPrice() throws Exception {
        var r = price("外架钢针-4件/双", DOUBLE_BAG, 4);
        assertThat(r.expectedUnitPrice).isCloseTo(8.3, org.assertj.core.data.Offset.offset(0.02));
    }
}
