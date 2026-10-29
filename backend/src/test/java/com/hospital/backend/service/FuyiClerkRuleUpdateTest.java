package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 2026-10-29 客户更正「字错」后的附一内勤规则：
 *  - ＜3 件：标准价格*0.8（原「系统价格 75 折」作废）
 *  - 敷料包(无纺布)：标准价格*0.8*0.999（原「系统价格*0.999」作废）
 *
 * 用附一 2026-06 真实账单抽样的价格作为输入，验证策略层按新比例出数。
 */
class FuyiClerkRuleUpdateTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private JsonNode zyyBaseline() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/billing-rules/baseline/ZYY-D1.json")) {
            assertThat(in).as("ZYY-D1 baseline 缺失").isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private Map<?, ?> fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fuyi-real-bill-june.json")) {
            assertThat(in).as("附一真实账单夹具缺失").isNotNull();
            return MAPPER.readValue(in, Map.class);
        }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Test
    void belowThreePiecesUsesEightyPercentOnRealBillRows() throws Exception {
        JsonNode rules = zyyBaseline();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) fixture().get("low");
        assertThat(rows).isNotEmpty();

        for (Map<String, Object> row : rows) {
            String type = (String) row.get("type");
            String name = (String) row.get("packName");
            String material = (String) row.get("material");
            int count = ((Number) row.get("count")).intValue();
            double base = ((Number) row.get("price")).doubleValue();

            var discount = BillingPolicyApplier.applyBillDetailDiscounts(
                    rules, type, name, material, "中医附一", base, count, false, false);

            assertThat(discount).as("应命中附一＜3件八折: " + name).isNotNull();
            assertThat(discount.price()).as("＜3 件应为 0.8 折: " + name)
                    .isCloseTo(round2(base * 0.8), within(0.02));
        }
    }

    @Test
    void nonWovenDressingStacksEightyPercentThenNineNineNine() throws Exception {
        JsonNode rules = zyyBaseline();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) fixture().get("nonwoven");
        assertThat(rows).isNotEmpty();

        for (Map<String, Object> row : rows) {
            String type = (String) row.get("type");
            String name = (String) row.get("packName");
            String material = (String) row.get("material");
            int count = ((Number) row.get("count")).intValue();
            double base = ((Number) row.get("price")).doubleValue();

            var discount = BillingPolicyApplier.applyBillDetailDiscounts(
                    rules, type, name, material, "中医附一", base, count, false, false);

            assertThat(discount).as("敷料无纺布应命中八折+九九九折: " + name).isNotNull();
            double expected = round2(round2(base * 0.8) * 0.999);
            assertThat(discount.price()).as("敷料无纺布应为 标准价*0.8*0.999: " + name)
                    .isCloseTo(expected, within(0.02));
        }
    }
}
