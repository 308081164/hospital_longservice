package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.mapper.CustomerBillingPolicyMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 附一折扣口径（2026-10-29 客户连续反馈「重复八折」后确认）：
 *
 * 附一的 standardPricingOverride 已经把合同价算好：
 *  - 高温纸塑阶梯 6.39/8.79/10.39/12.79（= 原价 × 0.8 × 0.999）
 *  - 高温无纺布 4.4/件、13.2 封顶（剖宫包 15 件 = 66.00）
 *  - 低温纸塑阶梯 17.58~27.97、敷料无纺布 19.98/23.98/27.97
 *
 * 因此**任何折扣策略再叠一次都是重复**。此测试用附一真实账单价格验证策略层不再扣减。
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

    @Test
    void noDiscountPolicyForAnyFuyiType() throws Exception {
        JsonNode policies = zyyBaseline().path("billingPolicies");
        assertThat(policies.isArray()).isTrue();
        for (JsonNode policy : policies) {
            assertThat(policy.path("policyType").asText())
                    .as("附一不应再有 DISCOUNT 策略（override 已含折扣）")
                    .isNotEqualTo("DISCOUNT");
        }
    }

    @Test
    void realBillRowsAreNotDiscountedAgain() throws Exception {
        JsonNode rules = zyyBaseline();
        Map<?, ?> fx = fixture();
        for (String key : List.of("low", "nonwoven")) {
            List<Map<String, Object>> rows = (List<Map<String, Object>>) fx.get(key);
            for (Map<String, Object> row : rows) {
                var discount = BillingPolicyApplier.applyBillDetailDiscounts(
                        rules,
                        (String) row.get("type"),
                        (String) row.get("packName"),
                        (String) row.get("material"),
                        "中医附一",
                        ((Number) row.get("price")).doubleValue(),
                        ((Number) row.get("count")).intValue(),
                        false, false);
                assertThat(discount).as("真实账单行不应再叠折扣: " + row.get("packName")).isNull();
            }
        }
    }

    @Test
    void instrumentPackKeepsContractPrice() throws Exception {
        // 剖宫包 15 件：override 高温无纺布 4.4/件 = 66.00，不应变成 52.80
        var discount = BillingPolicyApplier.applyBillDetailDiscounts(
                zyyBaseline(), "器械包", "剖宫包（产科）", "无纺布-90×90-50", "中医附一", 66.0, 15, false, false);
        assertThat(discount).as("剖宫包 66.00 不应再打八折").isNull();
    }
}
