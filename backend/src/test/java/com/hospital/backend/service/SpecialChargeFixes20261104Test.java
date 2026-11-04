package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 2026-11-04 客户反馈三问题回归：
 * 1) 市二院低温双层袋：铝箔袋应识别为低温纸塑袋，按 20cm=28 元收取（不得按无纺布 35 元）
 * 2) 骨伤钢针 + /双：0.3 元只加一次（16.80，而不是 17.10）
 * 3) 骨伤「包名称带钉」≥10 件：固定 50 元（不得因自加排除词回落标准价）
 */
class SpecialChargeFixes20261104Test {

    @Test
    void hrb2ndLowTempDoubleBagUsesFoillBagPrice() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-2ND");
        var one = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院", "额外包低温等离子", "泌尿内镜套件-1套/Z2060",
                "铝箔袋200*600，无纺布-120*220-60g", 1, 1, 38.00, 38.00));
        assertThat(one.expectedUnitPrice).isCloseTo(28.00, within(0.02));
        assertThat(one.pricingRule).contains("低温纸塑袋");

        var four = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院", "额外包低温等离子", "手术室-球内注药-2件/2",
                "无纺布-120*220-60g，铝箔袋200*600，无纺布-120*220-60g", 4, 1, 25.00, 25.00));
        assertThat(four.expectedUnitPrice).isCloseTo(88.00, within(0.02));
        assertThat(four.pricingRule).contains("低温纸塑袋");
    }

    @Test
    void gushangSteelNeedleAddonAppliedOnce() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        var r = engine.processRow(PricingEngineTestSupport.row(
                "哈尔滨市骨伤科医院", "额外包（纸塑袋）", "富康钢针-6扳手-1/双/z1029",
                "高温纸塑袋150*260", 7, 1, 16.80, 16.80));
        // 2×5.5=11 + 0.3（双）+ 内层袋 5.5 = 16.80，0.3 不得重复
        assertThat(r.expectedUnitPrice).isCloseTo(16.80, within(0.02));
        assertThat(r.pricingRule).doesNotContain("骨伤科钢针加收0.3");
    }

    @Test
    void gushangNailOverTenIsFixedFifty() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        for (String name : new String[]{"拉力钉-1-28件", "头钉-1-28件", "威客钉-28件"}) {
            var r = engine.processRow(PricingEngineTestSupport.row(
                    "哈尔滨市骨伤科医院", "额外包（纸塑袋）", name, "高温纸塑袋150*260", 28, 1, 143.20, 143.20));
            assertThat(r.expectedUnitPrice).as(name).isCloseTo(50.00, within(0.02));
        }
    }
}
