package com.hospital.backend.service;

import com.hospital.backend.entity.CustomerProductRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingRuleCompilerBillingModeTest {

    @Test
    void fixedPriceInfersPerPack() {
        CustomerProductRule rule = baseRule("FIXED_PRICE", 21.99);
        assertThat(BillingModeInference.inferFromRule(rule)).isEqualTo(BillingMode.PER_PACK);
        assertThat(BillingModeInference.inferRuleType(BillingMode.PER_PACK)).isEqualTo("FIXED_PRICE");
    }

    @Test
    void pricePerInstrumentInfersPerInstrument() {
        CustomerProductRule rule = baseRule("PRICE_PER_INSTRUMENT", 5.5);
        rule.setKeywords("[\"眼包\"]");
        assertThat(BillingModeInference.inferFromRule(rule)).isEqualTo(BillingMode.PER_INSTRUMENT);
        assertThat(BillingModeInference.defaultPieceCountSource(BillingMode.PER_INSTRUMENT))
                .isEqualTo(FixedPriceBillingCountResolver.PIECE_COUNT_SOURCE_EFFECTIVE);
    }

    @Test
    void guashaTanzhenInfersPackNameSuffix() {
        CustomerProductRule rule = baseRule("PRICE_PER_INSTRUMENT", 5.5);
        rule.setKeywords("[\"刮勺探针\"]");
        assertThat(BillingModeInference.inferFromRule(rule)).isEqualTo(BillingMode.PACK_NAME_SUFFIX);
        assertThat(BillingModeInference.defaultPieceCountSource(BillingMode.PACK_NAME_SUFFIX))
                .isEqualTo(FixedPriceBillingCountResolver.PIECE_COUNT_SOURCE_PACK_NAME_LAST_NUMBER);
    }

    @Test
    void explicitBillingModeOverridesInference() {
        CustomerProductRule rule = baseRule("FIXED_PRICE", 5.5);
        rule.setBillingMode("PACK_NAME_SUFFIX");
        assertThat(BillingModeInference.inferFromRule(rule)).isEqualTo(BillingMode.PACK_NAME_SUFFIX);
        assertThat(BillingModeInference.inferRuleType(BillingMode.PACK_NAME_SUFFIX))
                .isEqualTo("PRICE_PER_INSTRUMENT");
    }

    private CustomerProductRule baseRule(String ruleType, double price) {
        CustomerProductRule rule = new CustomerProductRule();
        rule.setId(1L);
        rule.setName("测试规则");
        rule.setRuleType(ruleType);
        rule.setPrice(java.math.BigDecimal.valueOf(price));
        return rule;
    }
}
