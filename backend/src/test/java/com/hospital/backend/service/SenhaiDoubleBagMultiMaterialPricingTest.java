package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 森海「/双」规则：包装材料列列出两层袋规时按标准高温纸塑袋费叠加，&lt;3 件封顶 16.5。
 */
class SenhaiDoubleBagMultiMaterialPricingTest {

    @Test
    void senhaiDoubleBagWithTwoMaterialsInColumnCapsAt165() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("SENHAI-YY");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨森海医院",
                "type", "额外包(纸塑袋)",
                "packName", "咬骨钳-1/双Z2032",
                "packageMaterial", "高温纸塑袋200*320 ，高温纸塑袋250*350",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 20.5,
                "totalPrice", 20.5
        ));

        assertThat(result.pricingRule).contains("森海双<3");
        assertThat(result.expectedUnitPrice).isCloseTo(16.5, within(0.001));
        assertThat(result.pricingPath).isEqualTo("fixed");
        assertThat(result.notes).noneMatch(note -> note.contains("需人工核对"));
    }

    @Test
    void senhaiDoubleBagSingleMaterialUsesPackNameInnerSize() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("SENHAI-YY");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨森海医院",
                "type", "额外包（纸塑袋）",
                "packName", "剪刀-1/双/z3040",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));

        assertThat(result.pricingRule).contains("森海双<3");
        assertThat(result.expectedUnitPrice).isCloseTo(16.5, within(0.001));
        assertThat(result.pricingPath).isEqualTo("fixed");
    }

    @Test
    void senhaiDoubleBagAtLeastThreeSkipsPackagingFee() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("SENHAI-YY");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨森海医院",
                "type", "额外包（纸塑袋）",
                "packName", "剪刀-3/双/z3040",
                "packageMaterial", "高温纸塑袋200*320 ，高温纸塑袋250*350",
                "instrumentCount", 3,
                "packCount", 1,
                "unitPrice", 16.5,
                "totalPrice", 16.5
        ));

        assertThat(result.pricingRule).contains("森海双≥3");
        assertThat(result.expectedUnitPrice).isCloseTo(16.5, within(0.001));
        assertThat(result.notes).noneMatch(note -> note.contains("纸塑袋费"));
    }
}
