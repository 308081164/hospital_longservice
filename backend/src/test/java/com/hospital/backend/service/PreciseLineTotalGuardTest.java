package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 先乘后舍入只作用在「折后还有第三位小数」的行。
 * 敷料包、整分一口价、内勤一口价不留下未收分单价。
 */
class PreciseLineTotalGuardTest {

    private final ReconciliationPricingOrchestrator orchestrator = new ReconciliationPricingOrchestrator(
            new ClerkBillPriceRuleApplier(),
            new ExportStageDiscountApplier());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dressingPackKeepsBillPrice() {
        Map<String, Object> row = new HashMap<>();
        row.put("type", "敷料包");
        row.put("packName", "纱布");
        row.put("packCount", 3);
        row.put("unitPrice", 16.50);
        row.put("totalPrice", 49.50);

        PricingEngine.ProcessedResult result = orchestrator.processRow(row, null, null, "SHENG-YY-NG", Set.of());

        assertThat(result.expectedUnitPrice).isCloseTo(16.50, within(0.001));
        assertThat(result.correctedTotalPrice).isCloseTo(49.50, within(0.001));
        assertThat(result.difference).isCloseTo(0.0, within(0.001));
        assertThat(result.unroundedUnitPrice).isNull();
        assertThat(result.billingNotes).doesNotContainKey(PreciseLineAmount.NOTE_KEY);
    }

    @Test
    void clerkFixedPriceIsNotReopenedByTheExtraFraction() {
        ObjectNode compiled = mapper.createObjectNode();
        ArrayNode rules = compiled.putArray("clerkRules");
        ObjectNode rule = rules.addObject();
        rule.put("ruleType", "BILL_EXPORT_PRICE_RULE");
        rule.put("name", "测试一口价");
        rule.put("stage", "bill_export");
        rule.put("isActive", true);
        ObjectNode params = rule.putObject("params");
        params.put("unitPrice", 100);
        params.put("unitPriceMode", "FIXED");
        ArrayNode policies = compiled.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "标准价六折");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode discount = policy.putObject("params");
        discount.put("applyStage", "export_only");
        discount.put("rate", 0.6);

        Map<String, Object> row = new HashMap<>();
        row.put("type", "器械包");
        row.put("packName", "一口价包");
        row.put("packCount", 3);
        row.put("instrumentCount", 1);
        row.put("unitPrice", 33.33);
        row.put("totalPrice", 99.99);

        PricingEngine.ProcessedResult result = orchestrator.processRow(
                row, null, compiled, "TEST", Set.of());

        assertThat(result.pricingPath).isEqualTo("clerk");
        assertThat(result.expectedUnitPrice).isCloseTo(33.33, within(0.001));
        assertThat(result.unroundedUnitPrice).isNull();
        assertThat(result.billingNotes).doesNotContainKey(PreciseLineAmount.NOTE_KEY);
        assertThat(PreciseLineAmount.correctedTotal(33.33, null, 3)).isEqualTo(99.99);
    }

    @Test
    void xiangfangSixtyPercentUsesTheSameTotalRoundingWithoutChangingTheRate() {
        ObjectNode compiled = new ClerkRuleCompiler(new ClerkRuleIndex()).compileForCustomer("SHENG-YY-XF");
        assertThat(compiled).isNotNull();
        double rate = compiled.path("billingPolicies").get(0).path("params").path("rate").asDouble();
        assertThat(rate).isEqualTo(0.6);

        BillRowItem three = base(36.38, 3);
        BillRowItem four = base(36.38, 4);
        List<BillRowItem> priced = new ExportStageDiscountApplier().apply(compiled, List.of(three, four));

        assertThat(priced.get(0).getUnitPrice()).isEqualTo(21.83);
        assertThat(priced.get(0).getCorrectedTotalPrice()).isEqualTo(65.48);
        assertThat(priced.get(1).getUnitPrice()).isEqualTo(21.83);
        assertThat(priced.get(1).getCorrectedTotalPrice()).isEqualTo(87.31);
        assertThat(compiled.path("billingPolicies").get(0).path("params").path("rate").asDouble()).isEqualTo(0.6);
    }

    private static BillRowItem base(double unit, int packs) {
        BillRowItem item = new BillRowItem();
        item.setType("器械包（ZSD）");
        item.setPackName("手术包-6件（双信封包装）");
        item.setPackageMaterial("无纺布");
        item.setPackCount(packs);
        item.setInstrumentCount(6);
        item.setUnitPrice(unit);
        item.setExpectedUnitPrice(unit);
        return item;
    }
}
