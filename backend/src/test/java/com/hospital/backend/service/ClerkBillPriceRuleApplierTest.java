package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkBillPriceRuleApplierTest {

    private final ClerkBillPriceRuleApplier applier = new ClerkBillPriceRuleApplier();
    private final ClerkRuleCompiler compiler = new ClerkRuleCompiler(new ClerkRuleIndex());

    @Test
    void appliesPerPiecePriceForDaowaiInstrumentPack() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("测试包");
        row.setInstrumentCount(5);
        row.setPackCount(1);
        row.setTotalPrice(100.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(15.0);
    }

    @Test
    void wildcardPackNameMatchesDressingRule() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("敷料包");
        row.setPackName("任意名称");
        row.setTotalPrice(30.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(19.0);
    }

    @Test
    void appliesPackNamePriceForObstetrics() {
        ObjectNode compiled = compiler.compileForCustomer("NG-FUCHAN");
        BillRowItem row = new BillRowItem();
        row.setType("器械包");
        row.setPackName("妇科腹腔镜手术包");
        row.setTotalPrice(200.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(170.5);
    }

    @Test
    void validatesSystemPriceWithoutChangingExportPrice() {
        ObjectNode compiled = compiler.compileForCustomer("DAOWAI-RM");
        BillRowItem row = new BillRowItem();
        row.setType("敷料包");
        row.setPackName("大敷料");
        row.setRowNumber(3);
        row.setTotalPrice(30.0);

        var result = applier.apply(compiled, List.of(row));
        assertThat(result.rows().get(0).getTotalPrice()).isEqualTo(19.0);
        assertThat(result.validationWarnings()).isNotEmpty();
    }
}
