package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 通用外来器械收费（特殊收费20261105 通用特殊收费 第24行 / 序号13）：
 * - 骨科租赁器械包-不带植入物：件数（含篮筐）× 5.5 元
 * - 骨科租赁器械包-带植入物：再 +150 元；市二院/市五院/骨伤科豁免
 * - 骨伤科 钉盒：固定 50 元
 */
class ExternalInstrumentPricingTest {

    private double price(String code, String hospital, String type, String name, int n) throws Exception {
        return PricingEngineTestSupport.engineForCustomerCode(code)
                .processRow(PricingEngineTestSupport.row(
                        hospital, type, name, "无纺布-90×90-50g", n, 1, 99, 99))
                .expectedUnitPrice;
    }

    @Test
    void implantPacksAddOneHundredFifty() throws Exception {
        // 12 × 5.5 = 66；+150 = 216
        assertThat(price("FNN-YY", "方南南医院", "骨科租赁器械包-带植入物", "外来器械-1套", 12))
                .isCloseTo(216.00, within(0.02));
    }

    @Test
    void exemptHospitalsDoNotAddOneHundredFifty() throws Exception {
        assertThat(price("HRB-2ND", "哈尔滨市第二医院", "骨科租赁器械包-带植入物", "外来器械-1套", 12))
                .isCloseTo(66.00, within(0.02));
        assertThat(price("HRB-WY", "哈尔滨市第五医院", "骨科租赁器械包-带植入物", "外来器械-1套", 12))
                .isCloseTo(66.00, within(0.02));
        assertThat(price("HRB-GUSHANG", "哈尔滨市骨伤科医院", "骨科租赁器械包-带植入物", "外来器械-1套", 12))
                .isCloseTo(66.00, within(0.02));
    }

    @Test
    void packWithoutImplantIsOnlyPerInstrumentPrice() throws Exception {
        assertThat(price("FNN-YY", "方南南医院", "骨科租赁器械包-不带植入物", "外来器械-1套", 12))
                .isCloseTo(66.00, within(0.02));
    }

    @Test
    void gushangNailBoxIsFixedFifty() throws Exception {
        assertThat(price("HRB-GUSHANG", "哈尔滨市骨伤科医院", "骨科租赁器械包-带植入物", "钉盒-1套", 3))
                .isCloseTo(50.00, within(0.02));
    }
}
