package com.hospital.backend.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFixedPriceApplierTest {

    private final ExportFixedPriceApplier applier = new ExportFixedPriceApplier();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appliesFixedPriceByKeywordBeforeExport() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ObjectNode special = rules.putObject("specialRules");
        ArrayNode fixedPrices = special.putArray("fixedPrices");
        ObjectNode rule = fixedPrices.addObject();
        rule.put("name", "长健敷料包");
        rule.put("price", 35);
        rule.putArray("keywords").add("敷料包/W12050");

        BillRowItem row = new BillRowItem();
        row.setPackName("敷料包/W12050");
        row.setType("敷料包(无纺布包)");
        row.setPackCount(1);
        row.setUnitPrice(25.0);
        row.setCorrectedTotalPrice(25.0);
        row.setStatus("unchanged");

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(35.0);
    }

    @Test
    void prefersFirstMatchingRuleInArray() throws Exception {
        ObjectNode rules = mapper.createObjectNode();
        ObjectNode special = rules.putObject("specialRules");
        ArrayNode fixedPrices = special.putArray("fixedPrices");
        ObjectNode wufang = fixedPrices.addObject();
        wufang.put("price", 35);
        wufang.putArray("keywords").add("大衣-无纺布");
        ObjectNode dayi = fixedPrices.addObject();
        dayi.put("price", 30);
        dayi.putArray("keywords").add("大衣");

        BillRowItem row = new BillRowItem();
        row.setPackName("大衣-无纺布/w15050");
        row.setPackCount(2);
        row.setCorrectedTotalPrice(60.0);
        row.setStatus("unchanged");

        List<BillRowItem> result = applier.apply(rules, List.of(row));
        assertThat(result.get(0).getCorrectedTotalPrice()).isEqualTo(70.0);
    }
}
