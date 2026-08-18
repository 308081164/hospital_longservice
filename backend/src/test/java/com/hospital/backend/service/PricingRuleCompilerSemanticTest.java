package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingRuleCompilerSemanticTest {

    @Test
    void extraFeeCompilesMinMaxInstrumentCount() throws Exception {
        Customer customer = new Customer();
        customer.setId(9001L);
        customer.setCode("TEST-EXTRA");
        customer.setCanonicalName("测试加收医院");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("special_only");

        CustomerProductRule extra = new CustomerProductRule();
        extra.setId(1625227L);
        extra.setIsActive(true);
        extra.setRuleType("EXTRA_FEE");
        extra.setName("件数边界加收");
        extra.setKeywords("[\"环钻\"]");
        extra.setFee(BigDecimal.valueOf(3));
        extra.setMinInstrumentCount(2);
        extra.setMaxInstrumentCount(5);

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(extra));
        JsonNode compiled = compiler.compileForCustomer(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        JsonNode node = compiled.path("specialRules").path("extraFees").get(0);
        assertThat(node.path("minInstrumentCount").asInt()).isEqualTo(2);
        assertThat(node.path("maxInstrumentCount").asInt()).isEqualTo(5);
        assertThat(node.path("fee").asDouble()).isEqualTo(3.0);
    }

    @Test
    void foldRuleCompilesThresholdAndSkipPackaging() throws Exception {
        Customer customer = new Customer();
        customer.setId(9002L);
        customer.setCode("TEST-FOLD");
        customer.setCanonicalName("测试折算医院");
        customer.setBillingEnabled(true);

        CustomerProductRule fold = new CustomerProductRule();
        fold.setId(90021L);
        fold.setIsActive(true);
        fold.setRuleType("FOLD");
        fold.setName("5合1含包材");
        fold.setKeywords("[\"指针\"]");
        fold.setThreshold(5);
        fold.setFoldRatio(BigDecimal.valueOf(5));
        fold.setMinInstrumentCount(null);
        fold.setMaxInstrumentCount(10);
        fold.setSkipPackaging(false);

        CustomerProductRule foldSkip = new CustomerProductRule();
        foldSkip.setId(90022L);
        foldSkip.setIsActive(true);
        foldSkip.setRuleType("FOLD");
        foldSkip.setName("5合1免包材");
        foldSkip.setKeywords("[\"指针\"]");
        foldSkip.setThreshold(5);
        foldSkip.setFoldRatio(BigDecimal.valueOf(5));
        foldSkip.setMinInstrumentCount(11);
        foldSkip.setSkipPackaging(true);

        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, List.of(fold, foldSkip));
        JsonNode compiled = compiler.compileForCustomer(
                com.hospital.backend.common.JsonUtils.getObjectMapper()
                        .valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        JsonNode folds = compiled.path("specialRules").path("foldRules");
        assertThat(folds).hasSize(2);
        assertThat(folds.get(0).path("maxInstrumentCount").asInt()).isEqualTo(10);
        assertThat(folds.get(1).path("skipPackaging").asBoolean()).isTrue();
    }

    @Test
    void manifestBingchengDoesNotCompileSmallPackagingExtra() throws Exception {
        JsonNode compiled = PricingEngineTestSupport.compileForCustomerCode("BINGCHENG-YM");
        JsonNode extras = compiled.path("specialRules").path("extraFees");
        JsonNode smallPack = null;
        for (JsonNode extra : extras) {
            if (extra.path("name").asText("").contains("小件包装")) {
                smallPack = extra;
                break;
            }
        }
        assertThat(smallPack).isNull();
    }
}
