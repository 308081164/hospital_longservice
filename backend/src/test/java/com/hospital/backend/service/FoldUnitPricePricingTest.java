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
 * FOLD 规则的 unitPrice（折算后单价）必须可从基线落库并在计价时生效。
 *
 * 背景（2026-10-27）：customer_product_rule 此前没有 unit_price 列，
 * baseline 里写 unitPrice 会在入库时被静默丢弃，规则变成「有规则无价格」。
 */
class FoldUnitPricePricingTest {

    private PricingEngine buildEngine(CustomerProductRule rule) throws Exception {
        Customer customer = new Customer();
        customer.setId(8100L);
        customer.setCanonicalName("折算单价测试医院");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("standard");
        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(rule));
        JsonNode compiled = compiler.compileForCustomer(
                JsonUtils.getObjectMapper().valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        return new PricingEngine(compiled);
    }

    private CustomerProductRule neizhenRule(Integer minCount) {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setId(810L);
        rule.setIsActive(true);
        rule.setRuleType("FOLD");
        rule.setName("市二院内热针按5折算");
        rule.setKeywords("[\"内热针\"]");
        rule.setKeywordMatchMode("contains");
        rule.setThreshold(5);
        rule.setFoldRatio(BigDecimal.valueOf(5));
        rule.setUnitPrice(BigDecimal.valueOf(5.5));
        rule.setSkipPackaging(true);
        rule.setMinInstrumentCount(minCount);
        return rule;
    }

    @Test
    void foldRuleUsesUnitPricePerFoldedCount() throws Exception {
        PricingEngine engine = buildEngine(neizhenRule(11));
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "折算单价测试医院",
                "type", "额外包(纸塑袋)",
                "packName", "内热针-12",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 12,
                "packCount", 1,
                "unitPrice", 60.0,
                "totalPrice", 60.0
        ));
        // 12 件 / 5 进一 = 3 个折算件 × 5.5 = 16.5
        assertThat(result.expectedUnitPrice).isNotNull();
        assertThat(result.expectedUnitPrice).isCloseTo(16.5, within(0.02));
    }

    @Test
    void foldRuleRoundsInstrumentCountUp() throws Exception {
        PricingEngine engine = buildEngine(neizhenRule(11));
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "折算单价测试医院",
                "type", "额外包(纸塑袋)",
                "packName", "内热针-11",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 11,
                "packCount", 1,
                "unitPrice", 60.0,
                "totalPrice", 60.0
        ));
        // 11 / 5 进一 = 3 × 5.5 = 16.5
        assertThat(result.expectedUnitPrice).isCloseTo(16.5, within(0.02));
    }
}
