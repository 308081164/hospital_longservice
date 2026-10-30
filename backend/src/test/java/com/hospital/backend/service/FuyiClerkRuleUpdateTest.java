package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 中医附一计价口径（2026-10-30 客户确认）：
 *  - 《附一收费标准.xlsx》作废封存，override 与 supplement 规则全部移除；
 *  - 只有两个权威来源：特殊收费20261026.xlsx 序号33（8 条）、内勤规则 91-96（6 条折扣）。
 *  - 标准价走通用引擎，再按内勤六条打折。
 */
class FuyiClerkRuleUpdateTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private JsonNode zyyBaseline() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/billing-rules/baseline/ZYY-D1.json")) {
            assertThat(in).as("ZYY-D1 baseline 缺失").isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private BillingPolicyApplier.BillDetailDiscount apply(String type, double price, int pieces) throws Exception {
        return BillingPolicyApplier.applyBillDetailDiscounts(
                zyyBaseline(), type, "测试包", "高温纸塑袋10cm", "中医附一", price, pieces, false, false);
    }

    @Test
    void onlyAuthoritativeRulesRemain() throws Exception {
        JsonNode baseline = zyyBaseline();
        assertThat(baseline.hasNonNull("standardPricingOverride"))
                .as("作废价表的 override 必须移除（显式 null 用于清库）")
                .isFalse();
        assertThat(baseline.path("productRules"))
                .as("只保留权威来源：特殊收费 20261026 + 内勤 10.30 的换药包固定价")
                .hasSize(7);
        for (JsonNode rule : baseline.path("productRules")) {
            String ref = rule.path("sourceRef").asText();
            assertThat(ref.contains("特殊收费20261026") || ref.contains("医院内勤规则10.30"))
                    .as("规则必须能追溯到权威表: " + ref)
                    .isTrue();
        }
    }

    @Test
    void paperPlasticBelowThreeUsesEightyPercentThenNineNineNine() throws Exception {
        // 10.30 新表：＜3 件 = 标准价*0.8*0.999 → 8.00 × 0.8 × 0.999 = 6.39
        var d = apply("额外包（纸塑袋）", 8.00, 1);
        assertThat(d).isNotNull();
        assertThat(d.price()).isCloseTo(6.39, within(0.02));
    }

    @Test
    void dressing120ClothHasFixedPrice2199() throws Exception {
        // 10.30 新表：换药包(120布) 器械数量无关，固定 21.99（按包名匹配的固定价规则，且跳过折扣）
        JsonNode baseline = zyyBaseline();
        JsonNode rule = null;
        for (JsonNode r : baseline.path("productRules")) {
            if ("附一换药包120布固定21.99".equals(r.path("name").asText())) {
                rule = r;
            }
        }
        assertThat(rule).as("换药包 21.99 固定价规则缺失").isNotNull();
        assertThat(rule.path("price").asDouble()).isEqualTo(21.99);
        assertThat(rule.path("skipDiscount").asBoolean()).as("固定价不能再被打折").isTrue();
        assertThat(rule.path("skipPackaging").asBoolean()).as("21.99 为含包材的最终价").isTrue();
    }

    @Test
    void nonWovenDressingUsesEightyPercentThenNineNineNine() throws Exception {
        var d = apply("敷料包（无纺布）", 35.00, 5);
        assertThat(d).isNotNull();
        // 35 × 0.8 × 0.999 = 27.97
        assertThat(d.price()).isCloseTo(27.97, within(0.02));
    }

    @Test
    void instrumentPackUsesEightyPercent() throws Exception {
        var d = apply("器械包", 66.00, 15);
        assertThat(d).isNotNull();
        assertThat(d.price()).isCloseTo(52.80, within(0.02));
    }

    @Test
    void dressingPackUsesEightyPercent() throws Exception {
        var d = apply("敷料包（纸塑袋）", 10.00, 1);
        assertThat(d).isNotNull();
        assertThat(d.price()).isCloseTo(8.00, within(0.02));
    }

    @Test
    void lowTempUsesEightyPercentThenNineNineNine() throws Exception {
        var d = apply("单包装包（等离子）", 28.00, 1);
        assertThat(d).isNotNull();
        // 28×0.8=22.40；再×0.999=22.38
        assertThat(d.price()).isCloseTo(22.38, within(0.02));
    }
}
