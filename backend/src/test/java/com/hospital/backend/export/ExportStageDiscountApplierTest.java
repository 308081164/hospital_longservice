package com.hospital.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportStageDiscountApplier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportStageDiscountApplierTest {

    private final ExportStageDiscountApplier applier = new ExportStageDiscountApplier();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appliesExportOnlyDiscountWithPieceTiers() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "太平导出折扣");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier1 = tiers.addObject();
        tier1.put("minPieces", 1);
        tier1.put("maxPieces", 1);
        tier1.put("rate", 1.0);
        tier1.put("decimalPlaces", 1);
        ObjectNode tier2 = tiers.addObject();
        tier2.put("minPieces", 2);
        tier2.put("rate", 0.75);
        tier2.put("decimalPlaces", 2);
        ArrayNode overrides = params.putArray("fixedPriceOverrides");
        ObjectNode override = overrides.addObject();
        override.put("from", 16.5);
        override.put("to", 8.91);

        BillRowItem single = row(1, 16.5);
        BillRowItem multi = row(3, 20.0);

        List<BillRowItem> result = applier.apply(rules, List.of(single, multi));

        assertThat(result.get(0).getUnitPrice()).isEqualTo(16.5);
        assertThat(result.get(1).getUnitPrice()).isEqualTo(15.0);
    }

    @Test
    void skipsWhenNoExportOnlyPolicy() {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "bill_detail");
        params.put("rate", 0.7);

        BillRowItem row = row(2, 100.0);
        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getUnitPrice()).isEqualTo(100.0);
    }

    @Test
    void skipsWhenRowAlreadyReflectsDiscountedUnitPrice() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("skipWhenAlreadyDiscounted", true);
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier = tiers.addObject();
        tier.put("minPieces", 2);
        tier.put("rate", 0.75);

        BillRowItem row = row(2, 15.0);
        row.setExpectedUnitPrice(20.0);
        row.setCorrectedTotalPrice(15.0);
        row.setTotalPrice(15.0);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(15.0);
        assertThat(result.get(0).getUnitPrice()).isEqualTo(15.0);
    }

    @Test
    void skipsWhenImportUnitAlreadyAtTierDiscountButExpectedUnitPriceWasDiscounted() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("skipWhenAlreadyDiscounted", true);
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier = tiers.addObject();
        tier.put("minPieces", 2);
        tier.put("rate", 0.75);

        BillRowItem row = row(2, 119.625);
        row.setExpectedUnitPrice(119.625);
        row.setCorrectedTotalPrice(119.625);
        row.setTotalPrice(119.625);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getUnitPrice()).isEqualTo(119.625);
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(119.625);
    }

    @Test
    void appliesDiscountFromOriginalImportWhenCorrectedStillFullPrice() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("skipWhenAlreadyDiscounted", true);
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier = tiers.addObject();
        tier.put("minPieces", 2);
        tier.put("rate", 0.75);

        BillRowItem row = row(2, 20.0);
        row.setExpectedUnitPrice(20.0);
        row.setCorrectedTotalPrice(40.0);
        row.setTotalPrice(40.0);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getUnitPrice()).isEqualTo(15.0);
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(15.0);
    }

    @Test
    void skipsWhenCorrectedTotalUsesFixedOverrideNotTierRate() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("skipWhenAlreadyDiscounted", true);
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier = tiers.addObject();
        tier.put("minPieces", 2);
        tier.put("rate", 0.75);
        ArrayNode overrides = params.putArray("fixedPriceOverrides");
        ObjectNode override = overrides.addObject();
        override.put("from", 16.5);
        override.put("to", 8.91);

        BillRowItem row = row(2, 8.91);
        row.setExpectedUnitPrice(16.5);
        row.setCorrectedTotalPrice(8.91);
        row.setTotalPrice(8.91);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(8.91);
    }

    @Test
    void revertsOverCorrectedEnginePriceToOriginalImport() throws Exception {
        ObjectNode rules = exportSkipRules();
        BillRowItem row = row(1, 20.63);
        row.setOriginal(java.util.Map.of("importUnitPrice", 16.5));
        row.setExpectedUnitPrice(20.63);
        row.setCorrectedTotalPrice(20.63);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getUnitPrice()).isEqualTo(16.5);
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(16.5);
    }

    @Test
    void revertsUnderPricedEngineToOriginalWhenTierIsIdentity() throws Exception {
        ObjectNode rules = exportSkipRules();
        BillRowItem row = row(1, 8.3);
        row.setOriginal(java.util.Map.of("importUnitPrice", 18.8));
        row.setExpectedUnitPrice(8.3);
        row.setCorrectedTotalPrice(8.3);

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getUnitPrice()).isEqualTo(18.8);
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(18.8);
    }

    @Test
    void tierThreeRateAppliesOnlyWhenOriginalUnitMatches() {
        List<BillingPolicyApplier.PieceTierDiscount> tiers = List.of(
                new BillingPolicyApplier.PieceTierDiscount(1, 2, 0.75, 2, null, null),
                new BillingPolicyApplier.PieceTierDiscount(3, 3, 0.891, 2, null, 16.5),
                new BillingPolicyApplier.PieceTierDiscount(4, 999, 0.75, 2, null, null));
        assertThat(BillingPolicyApplier.applyPieceTierRate(16.5, 3, tiers)).isEqualTo(14.7);
        assertThat(BillingPolicyApplier.applyPieceTierRate(20.0, 3, tiers)).isEqualTo(15.0);
    }

    private ObjectNode exportSkipRules() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ArrayNode policies = rules.putArray("billingPolicies");
        ObjectNode policy = policies.addObject();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", "太平导出折扣");
        policy.putObject("scope").put("temperature", "ANY");
        ObjectNode params = policy.putObject("params");
        params.put("applyStage", "export_only");
        params.put("skipWhenAlreadyDiscounted", true);
        ArrayNode tiers = params.putArray("pieceTierDiscounts");
        ObjectNode tier1 = tiers.addObject();
        tier1.put("minPieces", 1);
        tier1.put("maxPieces", 1);
        tier1.put("rate", 1.0);
        ObjectNode tier2 = tiers.addObject();
        tier2.put("minPieces", 2);
        tier2.put("rate", 0.75);
        return rules;
    }

    private static BillRowItem row(int instruments, double unitPrice) {
        BillRowItem item = new BillRowItem();
        item.setInstrumentCount(instruments);
        item.setPackCount(1);
        item.setUnitPrice(unitPrice);
        item.setExpectedUnitPrice(unitPrice);
        item.setType("器械包(纸塑袋)");
        item.setPackName("测试包");
        item.setPackageMaterial("纸塑袋");
        item.setStatus("unchanged");
        return item;
    }
}
