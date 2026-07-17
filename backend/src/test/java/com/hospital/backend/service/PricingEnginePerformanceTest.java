package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.service.DefaultPricingTemplate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-03：单 Job 万行计价性能基准（本地 CI 冒烟，阈值 30s）。
 */
class PricingEnginePerformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void processesTenThousandRowsUnderThirtySeconds() throws Exception {
        JsonNode compiled = MAPPER.valueToTree(DefaultPricingTemplate.buildRulesMap());
        PricingEngine engine = new PricingEngine(compiled);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("hospitalName", "性能测试院");
        template.put("type", "额外包(纸塑袋)");
        template.put("packName", "普通器械-4/Z7526");
        template.put("packageMaterial", "高温纸塑袋20cm");
        template.put("instrumentCount", 4);
        template.put("packCount", 1);
        template.put("unitPrice", 22);
        template.put("totalPrice", 22);

        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            engine.processRow(template);
        }
        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

        assertThat(elapsedSeconds).isLessThan(30.0);
    }
}
