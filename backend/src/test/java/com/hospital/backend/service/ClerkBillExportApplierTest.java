package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkBillExportApplierTest {

    private final ClerkBillExportApplier applier = new ClerkBillExportApplier();
    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void appliesZeroRowPackagingForWujing() {
        ObjectNode compiled = compiler.compileForCustomer("WUJING-ZD");
        assertThat(compiled).isNotNull();

        BillRowItem row = new BillRowItem();
        row.setPackName("普通包");
        row.setPackageMaterial("纸塑袋");
        row.setTotalPrice(0.0);
        row.setUnitPrice(0.0);

        List<BillRowItem> rows = new ArrayList<>(List.of(row));
        rows = applier.apply(compiled, rows);

        assertThat(rows.get(0).getTotalPrice()).isEqualTo(8.0);
    }

    @Test
    void appliesNonwovenFee() {
        ObjectNode compiled = JsonUtils.getObjectMapper().createObjectNode();
        var rules = JsonUtils.getObjectMapper().createArrayNode();
        var rule = JsonUtils.getObjectMapper().createObjectNode();
        rule.put("ruleType", "ZERO_ROW_PACKAGING_FEE");
        rule.put("isActive", true);
        rule.set("params", JsonUtils.getObjectMapper().createObjectNode()
                .put("paperPlastic", 8)
                .put("nonwoven", 20)
                .put("eto", 35));
        rules.add(rule);
        compiled.set("clerkRules", rules);

        BillRowItem row = new BillRowItem();
        row.setPackName("测试包");
        row.setPackageMaterial("无纺布");
        row.setTotalPrice(0.0);

        List<BillRowItem> rows = applier.apply(compiled, List.of(row));
        assertThat(rows.get(0).getTotalPrice()).isEqualTo(20.0);
    }
}
