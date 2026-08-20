package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归：系统不应镜像账单单价（改账单价后 expected 仍跟随变化）。
 */
class BillMirrorRegressionTest {

    @Test
    void hulanLaparoscopicLowTempPriceStableWhenBillChanges() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HULAN-HSZ");
        Map<String, Object> base = Map.of(
                "hospitalName", "呼兰区红十字医院",
                "department", "手术室",
                "type", "额外包(低温等离子)",
                "packName", "腹腔镜器械-7件/z2565",
                "packageMaterial", "低温纸塑袋250*650 ",
                "instrumentCount", 7,
                "packCount", 1,
                "totalPrice", 131.0);

        PricingEngine.ProcessedResult at131 = engine.processRow(withPrice(base, 131.0, 131.0));
        PricingEngine.ProcessedResult at132 = engine.processRow(withPrice(base, 132.0, 132.0));

        assertThat(at131.expectedUnitPrice).isNotNull();
        assertThat(at132.expectedUnitPrice).isEqualTo(at131.expectedUnitPrice);
        assertThat(at131.expectedUnitPrice).isNotEqualTo(131.0);
        assertThat(at131.status).isEqualTo("warning");
    }

    @Test
    void hljFyRkChezhenAnyPriceAcceptsEightButNotNine() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HLJ-FY-RK");
        Map<String, Object> base = Map.of(
                "hospitalName", "黑龙江省妇幼保健院（人口）",
                "department", "口腔科（樊医生）",
                "type", "额外包(纸塑袋)",
                "packName", "车针-4/Z7520",
                "packageMaterial", "高温纸塑袋75*200* ",
                "instrumentCount", 16,
                "packCount", 4);

        PricingEngine.ProcessedResult at8 = engine.processRow(withPrice(base, 8.0, 32.0));
        PricingEngine.ProcessedResult at9 = engine.processRow(withPrice(base, 9.0, 36.0));

        assertThat(at8.status).isEqualTo("unchanged");
        assertThat(at8.expectedUnitPrice).isEqualTo(8.0);

        assertThat(at9.status).isEqualTo("warning");
        assertThat(at9.expectedUnitPrice).isNotEqualTo(9.0);
    }

    @Test
    void unresolvedCustomerBillingDisabledYieldsWarningNotMirrorPass() throws Exception {
        ObjectNode rules = (ObjectNode) PricingEngineTestSupport.defaultRules();
        ObjectNode billingProfile = rules.putObject("billingProfile");
        billingProfile.put("enabled", false);
        billingProfile.put("pricingMode", "standard");

        PricingEngine engine = new PricingEngine(rules);
        Map<String, Object> base = Map.of(
                "hospitalName", "人口",
                "department", "口腔科",
                "type", "额外包(纸塑袋)",
                "packName", "车针-4/Z7520",
                "packageMaterial", "高温纸塑袋75*200* ",
                "instrumentCount", 16,
                "packCount", 4);

        PricingEngine.ProcessedResult at8 = engine.processRow(withPrice(base, 8.0, 32.0));
        PricingEngine.ProcessedResult at9 = engine.processRow(withPrice(base, 9.0, 36.0));

        assertThat(at8.status).isEqualTo("warning");
        assertThat(at9.status).isEqualTo("warning");
        assertThat(at8.expectedUnitPrice).isEqualTo(8.0);
        assertThat(at9.expectedUnitPrice).isEqualTo(9.0);
        assertThat(at8.pricingRule).isEqualTo("特色账单已关闭");
    }

    private static Map<String, Object> withPrice(Map<String, Object> base, double unitPrice, double totalPrice) {
        Map<String, Object> row = new java.util.LinkedHashMap<>(base);
        row.put("unitPrice", unitPrice);
        row.put("totalPrice", totalPrice);
        return row;
    }
}
