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
                .as("只保留特殊收费 Excel 的规则")
                .hasSize(8);
        for (JsonNode rule : baseline.path("productRules")) {
            assertThat(rule.path("sourceRef").asText())
                    .as("规则必须能追溯到特殊收费 20261026")
                    .contains("特殊收费20261026");
        }
    }

    @Test
    void paperPlasticBelowThreeUsesEightyPercent() throws Exception {
        var d = apply("额外包（纸塑袋）", 8.00, 1);
        assertThat(d).isNotNull();
        assertThat(d.price()).isCloseTo(6.40, within(0.02));
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
