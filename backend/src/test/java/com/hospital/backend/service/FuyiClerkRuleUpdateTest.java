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
 * 附一内勤规则（2026-10-29 客户两轮反馈后）：
 *  - 附一的 standardPricingOverride 阶梯价本身已是「0.8×0.999」后的成交价（如 10cm=6.39、敷料无纺布=27.97），
 *    因此这些类型**不得再叠折扣**，否则就是客户反馈的「重复八折」。
 *  - 仅未被 override 覆盖的类型（器械包/ZSD/单包装包）走 0.8 分段折扣。
 *  - 额外包（无纺布）保留 13.2 固定价与八折（override 不覆盖该类型）。
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
    void paperPlasticExtraPackDoesNotStackAnotherDiscountOnRealRows() throws Exception {
        JsonNode rules = zyyBaseline();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) fixture().get("low");
        assertThat(rows).isNotEmpty();

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
            // 6.39 等阶梯价已是成交价，客户明确「没有再八折了」
            assertThat(discount).as("额外包(纸塑袋) 不应再叠折扣: " + row.get("packName")).isNull();
        }
    }

    @Test
    void nonWovenDressingDoesNotStackAnotherDiscountOnRealRows() throws Exception {
        JsonNode rules = zyyBaseline();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) fixture().get("nonwoven");
        assertThat(rows).isNotEmpty();

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
            // 敷料无纺布阶梯 27.97 已含 0.8×0.999
            assertThat(discount).as("敷料无纺布不应再叠折扣: " + row.get("packName")).isNull();
        }
    }

    @Test
    void instrumentPacksBelowThreeStillUseEightyPercent() throws Exception {
        JsonNode rules = zyyBaseline();
        var discount = BillingPolicyApplier.applyBillDetailDiscounts(
                rules, "器械包（ZSD）", "换药包(120布)", "高温纸塑袋75*200", "中医附一", 10.0, 2, false, false);
        assertThat(discount).isNotNull();
        assertThat(discount.price()).isCloseTo(8.0, within(0.02));
    }
}
