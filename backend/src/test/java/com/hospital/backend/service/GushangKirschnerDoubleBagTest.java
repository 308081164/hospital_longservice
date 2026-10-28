package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 骨伤科克氏针「/双」双层袋再加 0.3（Excel 各医院特殊收费 230–238）。
 * 口径：细号 5.5 → 5.8；粗号 8 → 8.3；非「/双」不受影响。
 */
class GushangKirschnerDoubleBagTest {

    private static final String HOSPITAL = "哈尔滨市骨伤科医院";

    private PricingEngine.ProcessedResult price(String packName, int count) throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        return engine.processRow(PricingEngineTestSupport.row(
                HOSPITAL, "额外包（纸塑袋）", packName, "高温纸塑袋75*200", count, 1, 30.0, 30.0));
    }

    @Test
    void thinKirschnerDoubleBagAddsThirtyCents() throws Exception {
        var r = price("克氏针（0.8）-3件/双", 3);
        assertThat(r.expectedUnitPrice).isCloseTo(5.8, within(0.02));
    }

    @Test
    void thickKirschnerDoubleBagAddsThirtyCents() throws Exception {
        var r = price("克氏针（1.5）-4件/双", 4);
        assertThat(r.expectedUnitPrice).isCloseTo(8.3, within(0.02));
    }

    @Test
    void singleBagKirschnerKeepsBasePrice() throws Exception {
        assertThat(price("克氏针（0.8）-3件", 3).expectedUnitPrice).isCloseTo(5.5, within(0.02));
        assertThat(price("克氏针（1.5）-4件", 4).expectedUnitPrice).isCloseTo(8.0, within(0.02));
    }
}
