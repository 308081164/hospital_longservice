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
 * 人工核对规则：规则单价应展示账单原价，而非 0 或非法校正价。
 */
class PricingEngineManualReviewTest {

    @Test
    void manualReviewFixedPriceRulePreservesBillUnitPrice() throws Exception {
        Customer customer = dianliCustomer();
        CustomerProductRule manualReview = new CustomerProductRule();
        manualReview.setId(9901L);
        manualReview.setIsActive(true);
        manualReview.setRuleType("PRICE_PER_INSTRUMENT");
        manualReview.setName("电力骨牵引包人工核对");
        manualReview.setKeywords("[\"骨牵引包\"]");
        manualReview.setPrice(BigDecimal.ZERO);
        manualReview.setSkipDiscount(true);
        manualReview.setConditionsJson("[{\"field\":\"manualReview\",\"value\":true}]");

        PricingEngine engine = engineFor(customer, List.of(manualReview));
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", customer.getCanonicalName(),
                "type", "器械包",
                "packName", "骨牵引包",
                "packageMaterial", "",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 192.5,
                "totalPrice", 192.5
        ));

        assertThat(result.expectedUnitPrice).isCloseTo(192.5, within(0.001));
        assertThat(result.correctedTotalPrice).isCloseTo(192.5, within(0.001));
        assertThat(result.difference).isCloseTo(0.0, within(0.001));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingRule).isEqualTo("电力骨牵引包人工核对");
        assertThat(result.pricingPath).isEqualTo("preserve");
        assertThat(result.notes).anyMatch(note -> note.contains("需人工核对"));
        assertThat(result.billingNotes).isNotNull();
        assertThat(result.billingNotes.get("manualReview")).isEqualTo(true);
    }

    @Test
    void dianliBaselineWithoutCorrectionPriceDoesNotApplyCustomerFixedForXishoufu() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("ZY3-DIANLI");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "黑龙江省中医药大学附属第三医院（电力）",
                "type", "",
                "packName", "洗手服",
                "packageMaterial", "",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 35.0,
                "totalPrice", 35.0
        ));

        assertThat(result.pricingRule).doesNotContain("校正价");
        assertThat(result.pricingRule).doesNotContain("电力校正价");
        assertThat(result.pricingPath).isNotEqualTo("fixed");
        assertThat(result.expectedUnitPrice).isCloseTo(35.0, within(0.001));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingPath).isEqualTo("preserve");
        assertThat(result.notes).anyMatch(note -> note.contains("人工核对"));
    }

    @Test
    void aolanDoubleBagSlashRuleMarksManualReviewAndPreservesBillPrice() throws Exception {
        assertDoubleBagManualReview("AOLAN-YY", "奥兰医院", "奥兰双", "剪刀-1/双/z3040");
    }

    @Test
    void yuandongDoubleBagSlashRuleMarksManualReviewAndPreservesBillPrice() throws Exception {
        assertDoubleBagManualReview("YUANDONG-XN", "黑龙江省远东心脑血管医院", "远东双", "剪刀-1/双/z3040");
    }

    @Test
    void senhaiDoubleBagSlashRuleMarksManualReviewAndPreservesBillPrice() throws Exception {
        assertDoubleBagManualReview("SENHAI-YY", "哈尔滨森海医院", "森海双", "剪刀-1/双/z3040");
    }

    @Test
    void guoyaoDoubleBagSlashRuleMarksManualReviewAndPreservesBillPrice() throws Exception {
        assertDoubleBagManualReview("GUOYAO-2", "国药总医院第二院区", "电机厂双", "剪刀-1/双/z3040");
    }

    private static void assertDoubleBagManualReview(
            String customerCode,
            String hospitalName,
            String ruleNamePrefix,
            String packName) throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode(customerCode);
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", hospitalName,
                "type", "额外包（纸塑袋）",
                "packName", packName,
                "packageMaterial", "高温纸塑袋75*200",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 22.0,
                "totalPrice", 22.0
        ));

        assertThat(result.pricingRule).contains(ruleNamePrefix);
        assertThat(result.expectedUnitPrice).isCloseTo(22.0, within(0.001));
        assertThat(result.status).isEqualTo("warning");
        assertThat(result.pricingPath).isEqualTo("preserve");
        assertThat(result.notes).anyMatch(note -> note.contains("需人工核对"));
        assertThat(result.billingNotes).isNotNull();
        assertThat(result.billingNotes.get("manualReview")).isEqualTo(true);
    }

    @Test
    void dianliBaselineBoneTractionFallsBackToManualReviewWithBillPrice() throws Exception {
        PricingEngine engine = PricingEngineTestSupport.engineForCustomerCode("ZY3-DIANLI");
        PricingEngine.ProcessedResult result = engine.processRow(Map.of(
                "hospitalName", "黑龙江省中医药大学附属第三医院（电力）",
                "type", "器械包",
                "packName", "骨牵引包",
                "packageMaterial", "",
                "instrumentCount", 1,
                "packCount", 1,
                "unitPrice", 192.5,
                "totalPrice", 192.5
        ));

        assertThat(result.pricingRule).doesNotContain("校正价");
        assertThat(result.pricingRule).doesNotContain("电力骨牵引包人工核对");
        assertThat(result.expectedUnitPrice).isCloseTo(192.5, within(0.001));
        assertThat(result.status).isEqualTo("warning");
    }

    private static Customer dianliCustomer() {
        Customer customer = new Customer();
        customer.setId(9900L);
        customer.setCanonicalName("黑龙江省中医药大学附属第三医院（电力）");
        customer.setBillingEnabled(true);
        customer.setBillingPricingMode("hybrid");
        return customer;
    }

    private static PricingEngine engineFor(Customer customer, List<CustomerProductRule> rules) throws Exception {
        PricingRuleCompiler compiler = PricingEngineTestSupport.mockCompiler(customer, rules);
        JsonNode compiled = compiler.compileForCustomer(
                JsonUtils.getObjectMapper().valueToTree(DefaultPricingTemplate.buildRulesMap()),
                customer
        );
        return new PricingEngine(compiled);
    }
}
