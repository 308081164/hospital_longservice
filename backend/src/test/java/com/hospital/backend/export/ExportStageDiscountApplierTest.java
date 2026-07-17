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
