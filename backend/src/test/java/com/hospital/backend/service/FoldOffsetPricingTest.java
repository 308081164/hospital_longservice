package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * FOLD foldOffset：先扣除固定件数（如「钢针偏移测试包乙」里的 1 把扳手）再按比例折算。
 * 口径（客户 2026-10-27 确认）：(件数-1)/6 进一 ×5.5 + 5.5。
 */
class FoldOffsetPricingTest {

    private PricingEngine buildEngine() throws Exception {
        Customer customer = new Customer();
        customer.setId(8200L);
        customer.setCanonicalName("折算偏移测试医院");
        customer.setBillingEnabled(true);

        CustomerProductRule rule = new CustomerProductRule();
        rule.setId(820L);
        rule.setIsActive(true);
        rule.setRuleType("FOLD");
        rule.setName("钢针先扣1件再按6折算");
        rule.setKeywords("[\"钢针偏移测试\"]");
        rule.setKeywordMatchMode("contains");
        rule.setThreshold(6);
        rule.setFoldRatio(BigDecimal.valueOf(6));
        rule.setFoldOffset(1);
        rule.setUnitPrice(BigDecimal.valueOf(5.5));
        rule.setSkipPackaging(true);

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(rule));
        JsonNode compiled = compiler.compileForCustomer(
                JsonUtils.getObjectMapper().valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        return new PricingEngine(compiled);
    }

    private double priceOf(PricingEngine engine, String packName, int count) {
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "折算偏移测试医院",
                "type", "额外包(纸塑袋)",
                "packName", packName,
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", count,
                "packCount", 1,
                "unitPrice", 60.0,
                "totalPrice", 60.0
        ));
        return result.expectedUnitPrice == null ? -1 : result.expectedUnitPrice;
    }

    @Test
    void subtractsOffsetBeforeFolding() throws Exception {
        PricingEngine engine = buildEngine();
        // 12 件：(12-1)/6 进一 = 2，+1 件 = 3 × 5.5 = 16.5
        assertThat(priceOf(engine, "钢针偏移测试包甲", 12)).isCloseTo(16.5, within(0.02));
        // 7 件：(7-1)/6 = 1，+1 = 2 × 5.5 = 11.0
        assertThat(priceOf(engine, "钢针偏移测试包乙", 7)).isCloseTo(11.0, within(0.02));
        // 6 件：(6-1)/6 进一 = 1，+1 = 2 × 5.5 = 11.0
        assertThat(priceOf(engine, "钢针偏移测试包丙", 6)).isCloseTo(11.0, within(0.02));
    }

    @Test
    void offsetOnlyPackCountsAsSingleUnit() throws Exception {
        PricingEngine engine = buildEngine();
        // 1 件：扣除 1 件后剩 0，仅计 1 件 = 5.5（不是 2 件）
        assertThat(priceOf(engine, "钢针偏移测试包丁", 1)).isCloseTo(5.5, within(0.02));
    }
}
