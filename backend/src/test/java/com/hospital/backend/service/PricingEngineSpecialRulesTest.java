package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PricingEngineSpecialRulesTest {

    @Test
    void multiplierAndExtraFeeStackOnBasePrice() throws Exception {
        Customer customer = new Customer();
        customer.setId(8001L);
        customer.setCanonicalName("倍率加费医院");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("standard");

        CustomerProductRule multiplier = new CustomerProductRule();
        multiplier.setId(801L);
        multiplier.setIsActive(true);
        multiplier.setRuleType("MULTIPLIER");
        multiplier.setName("客户特色倍率");
        multiplier.setKeywords("[\"特殊包\"]");
        multiplier.setMultiplier(BigDecimal.valueOf(1.5));

        CustomerProductRule extraFee = new CustomerProductRule();
        extraFee.setId(802L);
        extraFee.setIsActive(true);
        extraFee.setRuleType("EXTRA_FEE");
        extraFee.setName("客户特色加收");
        extraFee.setKeywords("[\"特殊包\"]");
        extraFee.setFee(BigDecimal.valueOf(12));

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(multiplier, extraFee));
        JsonNode compiled = compiler.compileForCustomer(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "倍率加费医院",
                "type", "额外包(纸塑袋)",
                "packName", "特殊包-4/Z7526",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 22,
                "totalPrice", 22
        ));
        assertThat(result.notes).anyMatch(n -> n.contains("客户特色倍率") || n.contains("客户特色加收"));
    }

    @Test
    void anyPriceFixedRuleMatchesAcceptedPrices() throws Exception {
        Customer customer = new Customer();
        customer.setId(8002L);
        customer.setCanonicalName("多报价医院");
        customer.setBillingEnabled(true);

        CustomerProductRule multiPrice = new CustomerProductRule();
        multiPrice.setId(803L);
        multiPrice.setIsActive(true);
        multiPrice.setRuleType("FIXED_PRICE");
        multiPrice.setMatchMode("any_price");
        multiPrice.setName("小腔包");
        multiPrice.setKeywords("[\"小腔包\"]");
        multiPrice.setPrice(BigDecimal.valueOf(71));
        multiPrice.setAcceptedPrices("[71,76.5]");
        multiPrice.setSkipPackaging(true);

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(multiPrice));
        JsonNode compiled = compiler.compileForCustomer(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        PricingEngine engine = new PricingEngine(compiled);
        PricingEngine.ProcessedResult matched = engine.processRow(Map.of(
                "hospitalName", "多报价医院",
                "type", "额外包(纸塑袋)",
                "packName", "小腔包-1",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 76.5,
                "totalPrice", 76.5
        ));
        assertThat(matched.expectedUnitPrice).isCloseTo(76.5, within(0.05));
        assertThat(matched.pricingRule).isEqualTo("小腔包");
    }

    @Test
    void suofeiFaceNeedleSkipsPackagingAtThreePieces() throws Exception {
        Customer customer = new Customer();
        customer.setId(8003L);
        customer.setCanonicalName("索菲医疗美容门诊");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("standard");

        CustomerProductRule perPiece = new CustomerProductRule();
        perPiece.setId(804L);
        perPiece.setIsActive(true);
        perPiece.setRuleType("PRICE_PER_INSTRUMENT");
        perPiece.setName("索菲面吸针按件5.5");
        perPiece.setKeywords("[\"面吸针\"]");
        perPiece.setPrice(BigDecimal.valueOf(5.5));
        perPiece.setMinInstrumentCount(3);
        perPiece.setSkipPackaging(true);

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(perPiece));
        JsonNode compiled = compiler.compileForCustomer(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        PricingEngine engine = new PricingEngine(compiled);

        PricingEngine.ProcessedResult fivePieces = engine.processRow(Map.of(
                "hospitalName", "索菲医疗美容门诊",
                "type", "额外包(纸塑袋)",
                "packName", "面吸针-5",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 5,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));
        assertThat(fivePieces.status).isEqualTo("warning");
        assertThat(fivePieces.expectedUnitPrice).isCloseTo(27.5, within(0.05));

        PricingEngine.ProcessedResult fourPieces = engine.processRow(Map.of(
                "hospitalName", "索菲医疗美容门诊",
                "type", "额外包(纸塑袋)",
                "packName", "面吸针-4",
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 4,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));
        assertThat(fourPieces.expectedUnitPrice).isCloseTo(22.0, within(0.05));
    }
}
