package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驱血带 W 码规格解析回归：W5050/W9050 等「W规格+型号后缀」形态。
 */
class TourniquetWCodeMeasureTest {

    @Test
    void hrbWyEmW5050TourniquetPricesAt25() throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("HRB-WY-EM");
        PricingEngine engine = new PricingEngine(rules);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "哈尔滨市第五医院（二门诊）",
                "department", "手术室",
                "type", "敷料包(无纺布包)",
                "packName", "驱血带/W5050",
                "packageMaterial", "无纺布-50x50-50g",
                "instrumentCount", 0,
                "packCount", 2,
                "unitPrice", 0.0,
                "totalPrice", 0.0
        ));
        assertThat(result.expectedUnitPrice).isEqualTo(25.0);
        assertThat(result.pricingRule).contains("驱血带——50");
        assertThat(result.notes).anyMatch(note -> note.contains("规格 50"));
    }

    @ParameterizedTest(name = "{0} → {1} 元")
    @CsvSource({
            "驱血带/W5050, 无纺布-50x50-50g, 25",
            "驱血带/W6050, 无纺布-60×60-50g, 25",
            "驱血带/W9050, 无纺布-90×90-50g, 30",
            "驱血带/W15050, , 35",
            "驱血带(高温)/W90, 无纺布-90×90-50g, 30",
    })
    void tourniquetWCodeSuffixPricing(String packName, String material, double expectedPrice) throws Exception {
        JsonNode rules = RuleFidelityTestSupport.compileForCustomerCode("HRB-WY-EM");
        PricingEngine engine = new PricingEngine(rules);
        var row = new java.util.HashMap<String, Object>();
        row.put("hospitalName", "哈尔滨市第五医院（二门诊）");
        row.put("department", "手术室");
        row.put("type", "敷料包(无纺布包)");
        row.put("packName", packName);
        row.put("packageMaterial", material == null || material.isEmpty() ? "" : material);
        row.put("instrumentCount", 0);
        row.put("packCount", 1);
        row.put("unitPrice", 0.0);
        row.put("totalPrice", 0.0);
        PricingEngine.ProcessedResult result = engine.processRow(row);
        assertThat(result.expectedUnitPrice).isEqualTo(expectedPrice);
        assertThat(result.pricingRule).contains("驱血带");
    }
}
