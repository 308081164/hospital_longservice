package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨伤科「钢针-几件 扳手1」：(件数-1)/6 进一 ×5.5 +5.5（由 foldOffset 提供）+ 内层纸塑袋 +0.3。
 * 口径由客户 2026-10-27 确认；包名格式 2026-10-28 确认为「钢针-8件 扳手1」。
 */
class GushangSteelNeedlePricingTest {

    private static final String HOSPITAL = "哈尔滨市骨伤科医院";

    private double priceOf(String packName, int count) throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                HOSPITAL, "额外包（纸塑袋）", packName, "高温纸塑袋75*200", count, 1, 30.0, 30.0));
        return result.expectedUnitPrice == null ? -1 : result.expectedUnitPrice;
    }

    @Test
    void eightNeedlesFoldToOnePlusWrench() throws Exception {
        // (8-1)/6 = 1.17 → 进一 2；+1 把扳手 = 3 × 5.5 = 16.5，再叠加袋费与 0.3
        double price = priceOf("钢针-8件 扳手1", 8);
        // 3 × 5.5 = 16.5 + 标准袋费 2.5 + 0.3 = 19.3
        assertThat(price).isCloseTo(19.3, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void singleWrenchOnlyCountsOneUnit() throws Exception {
        // 1 件：扣除 1 件后剩 0，仅 1 件 × 5.5 = 5.5，再叠加袋费与 0.3
        double price = priceOf("钢针-1件 扳手1", 1);
        // 1 × 5.5 + 2.5 + 0.3 = 8.3
        assertThat(price).isCloseTo(8.3, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void externalFrameNeedleIsNotAffected() throws Exception {
        // 外架钢针走既有 <=6 固定 8.3，不应被钢针折算规则抢走
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("HRB-GUSHANG");
        PricingEngine.ProcessedResult result = engine.processRow(PricingEngineTestSupport.row(
                HOSPITAL, "额外包（纸塑袋）", "外架钢针-4件/双", "高温纸塑袋75*200", 4, 1, 30.0, 30.0));
        assertThat(result.pricingRule).doesNotContain("钢针先扣扳手");
    }
}
