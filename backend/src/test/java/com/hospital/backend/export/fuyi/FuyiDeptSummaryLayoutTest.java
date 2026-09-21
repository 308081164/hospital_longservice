package com.hospital.backend.export.fuyi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FuyiDeptSummaryLayoutTest {

    @Test
    void buildUsesJuneGoldStandardLeftOrderWithWashFee() {
        Map<String, Double> totals = new LinkedHashMap<>();
        totals.put("手术室(一区)", 43420.8);
        totals.put("手术室(二区)", 5278.4);

        FuyiDeptSummaryLayout.Layout layout = FuyiDeptSummaryLayout.build(totals, 3987.1, 2295.0);

        assertThat(layout.left()).hasSize(28);
        assertThat(layout.left().get(0).department()).isEqualTo("手术室(一区)");
        assertThat(layout.left().get(1).department()).isEqualTo(FuyiDeptSummaryLayout.WASH_FEE_LABEL);
        assertThat(layout.left().get(1).amount()).isEqualTo(3987.1);
        assertThat(layout.left().get(2).department()).isEqualTo("手术室(二区)");
        assertThat(layout.left().get(27).department()).isEqualTo("消化二科");
        assertThat(layout.left().stream().map(FuyiDeptSummaryLayout.DeptEntry::department))
                .doesNotContain("心内五");
    }

    @Test
    void buildUsesJuneGoldStandardRightOrder() {
        FuyiDeptSummaryLayout.Layout layout = FuyiDeptSummaryLayout.build(Map.of(), null, 2295.0);

        assertThat(layout.right()).hasSize(28);
        assertThat(layout.right().get(0).department()).isEqualTo("产科");
        assertThat(layout.right().get(9).department()).isEqualTo("透析");
        assertThat(layout.right().get(18).department()).isEqualTo("针灸五");
        assertThat(layout.right().get(22).department()).isEqualTo("血液");
        assertThat(layout.right().get(26).department()).isEqualTo("康复二");
        assertThat(layout.right().get(27).department()).isEqualTo(FuyiDeptSummaryLayout.LOGISTICS_LABEL);
        assertThat(layout.right().get(27).amount()).isEqualTo(2295.0);
    }

    @Test
    void buildFallsBackToFebruaryStyleLeftWhenNoWashFee() {
        FuyiDeptSummaryLayout.Layout layout = FuyiDeptSummaryLayout.build(Map.of("心内五", 8.8), null, null);

        assertThat(layout.left()).hasSize(28);
        assertThat(layout.left().get(27).department()).isEqualTo("心内五");
        assertThat(layout.left().get(27).amount()).isEqualTo(8.8);
        assertThat(layout.left().stream().map(FuyiDeptSummaryLayout.DeptEntry::department))
                .doesNotContain(FuyiDeptSummaryLayout.WASH_FEE_LABEL);
    }

    @Test
    void buildMapsBloodSheetAliasToBloodSlot() {
        Map<String, Double> totals = Map.of("血液科", 38.4);
        FuyiDeptSummaryLayout.Layout layout = FuyiDeptSummaryLayout.build(totals, null, null);

        FuyiDeptSummaryLayout.DeptEntry blood = layout.right().get(22);
        assertThat(blood.department()).isEqualTo("血液");
        assertThat(blood.amount()).isEqualTo(38.4);
    }
}
