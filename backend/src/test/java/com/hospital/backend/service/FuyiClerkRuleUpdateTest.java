package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 附一折扣口径（2026-10-29 客户多轮反馈后的最终形态）：
 *
 *  不打折（override 阶梯价已是成交价）：
 *    额外包（纸塑袋）6.39、器械包/器械包（ZSD）66.00（15 件高温无纺布）
 *  8 折：
 *    敷料包、敷料包（纸塑袋）
 *  8 折再 999 折：
 *    单包装包（EO/等离子）、额外包（ETO/低温等离子）
 *  额外包（无纺布）<3：固定 13.2
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
    void paperPlasticExtraPackAndInstrumentPackKeepContractPrice() throws Exception {
        // 客户：6.39 已对，不能再八折；器械包 66.00 同理
        assertThat(apply("额外包（纸塑袋）", 6.39, 1)).isNull();
        assertThat(apply("器械包", 66.00, 15)).isNull();
        assertThat(apply("器械包（ZSD）", 66.00, 15)).isNull();
    }

    @Test
    void dressingPackUsesEightyPercent() throws Exception {
        var d = apply("敷料包（纸塑袋）", 10.0, 1);
        assertThat(d).isNotNull();
        assertThat(d.price()).isCloseTo(8.0, within(0.02));
    }

    @Test
    void eoAndEtoPacksUseEightyPercentThenNineNineNine() throws Exception {
        for (String type : new String[]{"单包装包（EO）", "额外包（ETO）"}) {
            var d = apply(type, 44.0, 6);
            assertThat(d).as(type + " 应命中 8 折 + 999 折").isNotNull();
            // 44×0.8=35.2；再×0.999=35.16
            assertThat(d.price()).isCloseTo(35.16, within(0.02));
            assertThat(d.note()).contains("0.999");
        }
    }

    @Test
    void plasmaTypesAlreadyDiscountedByOverride() throws Exception {
        // 低温纸塑袋阶梯（22.38 等）本身已含 0.8×0.999，不能再折（客户 2026-10-30 截图）
        assertThat(apply("单包装包（等离子）", 22.38, 1)).isNull();
        assertThat(apply("额外包（低温等离子）", 22.38, 1)).isNull();
        assertThat(apply("单包装包（老肯低温）", 22.38, 1)).isNull();
    }
}
