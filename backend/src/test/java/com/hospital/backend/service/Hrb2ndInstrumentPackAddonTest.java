package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 哈尔滨市第二医院器械包「件数×5.5 + 8」（特殊收费20261026 序号41 ⑤）。
 *
 * 客户 2026-11-03 反馈：账单里包名是「刮宫包 / 剖宫包-4件 / 电切包 / 骨科显微包」，
 * 而规则关键词写的是「手术室-刮宫包-10件」等带前缀的名字，导致规则没命中，
 * 回落到「高温无纺布计费」（件数×5.5），每包少收 8 元。
 */
class Hrb2ndInstrumentPackAddonTest {

    private PricingEngine engine() throws Exception {
        return PricingEngineTestSupport.engineForCustomerCode("HRB-2ND");
    }

    private double price(String packName, String type, int instruments) throws Exception {
        return engine().processRow(PricingEngineTestSupport.row(
                "哈尔滨市第二医院", type, packName, "无纺布-120*120-50g", instruments, 1, 999, 999))
                .expectedUnitPrice;
    }

    @Test
    void prefixedAndBarePackNamesBothGetPlusEight() throws Exception {
        for (String type : List.of("器械包（ZSD）", "器械包")) {
            // 44 × 5.5 + 8 = 250；48 × 5.5 + 8 = 272；20 × 5.5 + 8 = 118
            assertThat(price("手术室-刮宫包-10件", type, 44)).isCloseTo(250.0, within(0.02));
            assertThat(price("刮宫包", type, 44)).isCloseTo(250.0, within(0.02));
            assertThat(price("剖宫包-4件", type, 48)).isCloseTo(272.0, within(0.02));
            assertThat(price("电切包", type, 20)).isCloseTo(118.0, within(0.02));
            assertThat(price("骨科显微包", type, 44)).isCloseTo(250.0, within(0.02));
        }
    }
}
