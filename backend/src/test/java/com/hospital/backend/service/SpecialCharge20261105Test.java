package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 特殊收费 2026-11-05 权威表变更回归：
 * - 骨伤科 序号12：骨科包等 21 个器械包 → 件数×5.5 + 5 元
 * - 市二院 ⑨：针线盒/缝合针 关键词放宽（去掉「（几根针）」「-几件」限定）
 */
class SpecialCharge20261105Test {

    @Test
    void gushangOrthopedicPackListAddsFive() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        record C(String name, int n, double expected) {}
        for (C c : new C[]{
                new C("骨科包", 5, 32.5),
                new C("股骨倒打髓针器械（二）12件", 12, 71.0),
                new C("关节镜器械(一号)19件", 19, 109.5),
                new C("股骨髓针-50件", 50, 280.0)}) {
            var r = engine.processRow(PricingEngineTestSupport.row(
                    "哈尔滨市骨伤科医院", "器械包", c.name(), "无纺布-90×90-50g", c.n(), 1, 99, 99));
            assertThat(r.expectedUnitPrice).as(c.name()).isCloseTo(c.expected(), within(0.02));
            assertThat(r.pricingRule).contains("骨伤科骨科包等按件5.5加5");
        }
    }

    @Test
    void hrb2ndNeedleBoxAndSutureKeywordsWidened() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-2ND");
        for (String name : new String[]{"针线盒（3根针）", "缝合针-5件"}) {
            var r = engine.processRow(PricingEngineTestSupport.row(
                    "哈尔滨市第二医院", "额外包（纸塑袋）", name, "高温纸塑袋75*200", 3, 1, 99, 99));
            assertThat(r.expectedUnitPrice).as(name).isCloseTo(5.50, within(0.02));
        }
    }
}
