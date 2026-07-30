package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedPriceBillingCountResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void perPackUsesBasePriceOnly() throws Exception {
        ObjectNode rule = rule(21.99, "PER_PACK");
        var row = new FixedPriceBillingCountResolver.RowInput(
                "器械包(ZSD)", "换药包", "换药包", 2, 4);
        var result = FixedPriceBillingCountResolver.compute(rule, row, 2);
        assertThat(result).isNotNull();
        assertThat(result.unitPrice()).isEqualTo(21.99);
        assertThat(result.totalPrice()).isEqualTo(43.98);
    }

    @Test
    void perInstrumentUsesEffectiveCount() throws Exception {
        ObjectNode rule = rule(5.5, "PER_INSTRUMENT");
        var row = new FixedPriceBillingCountResolver.RowInput(
                "额外包(纸塑袋)", "挖勺-2/z7530", "挖勺-2", 4, 8);
        var result = FixedPriceBillingCountResolver.compute(rule, row, 2);
        assertThat(result).isNotNull();
        assertThat(result.unitPrice()).isEqualTo(11.0);
        assertThat(result.totalPrice()).isEqualTo(44.0);
    }

    @Test
    void zsdMultiPackUsesPerPackInstrumentCount() throws Exception {
        ObjectNode rule = rule(5.5, "PER_INSTRUMENT");
        var row = new FixedPriceBillingCountResolver.RowInput(
                "器械包(ZSD)", "眼包", "眼包", 2, 28);
        var result = FixedPriceBillingCountResolver.compute(rule, row, 28);
        assertThat(result).isNotNull();
        assertThat(result.unitPrice()).isEqualTo(77.0);
        assertThat(result.totalPrice()).isEqualTo(154.0);
    }

    @Test
    void packNameSuffixExtractsLastNumberBeforeSlash() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("price", 5.5);
        rule.put("billingMode", "PACK_NAME_SUFFIX");
        rule.put("pieceCountSource", "PACK_NAME_LAST_NUMBER");
        var row = new FixedPriceBillingCountResolver.RowInput(
                "额外包(纸塑袋)", "刮勺探针4/z1035", "刮勺探针4", 1, 8);
        var result = FixedPriceBillingCountResolver.compute(rule, row, 1);
        assertThat(result).isNotNull();
        assertThat(result.pieceCount()).isEqualTo(4);
        assertThat(result.unitPrice()).isEqualTo(22.0);
    }

    @Test
    void infersPackNameSuffixFromLegacyKeyword() throws Exception {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("price", 5.5);
        rule.put("pricePerInstrument", true);
        rule.putArray("keywords").add("刮勺探针");
        assertThat(FixedPriceBillingCountResolver.resolveBillingMode(rule))
                .isEqualTo(BillingMode.PACK_NAME_SUFFIX);
    }

    private static ObjectNode rule(double price, String billingMode) {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("price", price);
        rule.put("billingMode", billingMode);
        if (!"PER_PACK".equals(billingMode)) {
            rule.put("pricePerInstrument", true);
        }
        return rule;
    }
}
