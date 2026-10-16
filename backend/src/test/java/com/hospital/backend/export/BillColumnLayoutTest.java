package com.hospital.backend.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillColumnLayoutTest {

    @Test
    void standardLayoutHasEightHeaders() {
        BillColumnLayout layout = BillColumnLayout.STANDARD_8COL;
        assertThat(layout.getHeaders()).hasSize(8);
        assertThat(layout.getHeaders()[5]).isEqualTo("包数");
        assertThat(layout.getHeaders()[6]).isEqualTo("单价");
        assertThat(layout.getHeaders()[7]).isEqualTo("总价");
        assertThat(layout.getMaxColIndex()).isEqualTo(10);
        assertThat(layout.maxColLetter()).isEqualTo("K");
        assertThat(layout.isExtended()).isFalse();
    }

    @Test
    void fuyiLayoutHasElevenHeaders() {
        BillColumnLayout layout = BillColumnLayout.FUYI_EXTENDED_11COL;
        assertThat(layout.getHeaders()).hasSize(11);
        assertThat(layout.getHeaders()[5]).isEqualTo("包数");
        assertThat(layout.getHeaders()[6]).isEqualTo("包装材料");
        assertThat(layout.getHeaders()[7]).isEqualTo("单包内器械数量/把");
        assertThat(layout.getHeaders()[8]).isEqualTo("单价（把）");
        assertThat(layout.getHeaders()[9]).isEqualTo("单价");
        assertThat(layout.getHeaders()[10]).isEqualTo("总价");
        assertThat(layout.getMaxColIndex()).isEqualTo(13);
        assertThat(layout.maxColLetter()).isEqualTo("N");
        assertThat(layout.isExtended()).isTrue();
    }

    @Test
    void fromKeyResolvesLayouts() {
        assertThat(BillColumnLayout.fromKey(null)).isEqualTo(BillColumnLayout.STANDARD_8COL);
        assertThat(BillColumnLayout.fromKey("")).isEqualTo(BillColumnLayout.STANDARD_8COL);
        assertThat(BillColumnLayout.fromKey("standard_8col")).isEqualTo(BillColumnLayout.STANDARD_8COL);
        assertThat(BillColumnLayout.fromKey("standard_with_instrument"))
                .isEqualTo(BillColumnLayout.STANDARD_WITH_INSTRUMENT);
        assertThat(BillColumnLayout.fromKey("standard_with_material_and_instrument"))
                .isEqualTo(BillColumnLayout.STANDARD_WITH_MATERIAL_AND_INSTRUMENT);
        assertThat(BillColumnLayout.fromKey("fuyi_extended_11col")).isEqualTo(BillColumnLayout.FUYI_EXTENDED_11COL);
        assertThat(BillColumnLayout.fromKey("unknown")).isEqualTo(BillColumnLayout.STANDARD_8COL);
    }

    @Test
    void instrumentLayoutInsertsCountBetweenPackAndUnitPrice() {
        BillColumnLayout layout = BillColumnLayout.STANDARD_WITH_INSTRUMENT;
        assertThat(layout.getHeaders()).containsExactly(
                "发货日期", "发货单号", "类型", "包类别号", "包名", "包数", "器械数", "单价", "总价");
        assertThat(layout.getMaxColIndex()).isEqualTo(11);
        assertThat(layout.maxColLetter()).isEqualTo("L");
        assertThat(layout.packCountColIndex()).isEqualTo(8);
        assertThat(layout.dataColumnIndex("器械数")).isEqualTo(9);
        assertThat(layout.unitPriceColIndex()).isEqualTo(10);
        assertThat(layout.totalPriceColIndex()).isEqualTo(11);
        assertThat(layout.showsInstrumentCount()).isTrue();
        assertThat(layout.isExtended()).isFalse();
        assertThat(BillColumnLayout.STANDARD_8COL.showsInstrumentCount()).isFalse();
        assertThat(layout.showsPackageMaterial()).isFalse();
    }

    @Test
    void materialAndInstrumentLayoutKeepsBothColumnsWithoutPerPiecePrice() {
        BillColumnLayout layout = BillColumnLayout.STANDARD_WITH_MATERIAL_AND_INSTRUMENT;
        assertThat(layout.getHeaders()).containsExactly(
                "发货日期", "发货单号", "类型", "包类别号", "包名", "包数",
                "包装材料", "器械数", "单价", "总价");
        assertThat(layout.getMaxColIndex()).isEqualTo(12);
        assertThat(layout.maxColLetter()).isEqualTo("M");
        assertThat(layout.dataColumnIndex("包装材料")).isEqualTo(9);
        assertThat(layout.dataColumnIndex("器械数")).isEqualTo(10);
        assertThat(layout.unitPriceColIndex()).isEqualTo(11);
        assertThat(layout.totalPriceColIndex()).isEqualTo(12);
        assertThat(layout.showsPackageMaterial()).isTrue();
        assertThat(layout.showsInstrumentCount()).isTrue();
        assertThat(layout.isExtended()).isFalse();
        assertThat(layout.getHeaders()).doesNotContain("单价（把）");
    }

    @Test
    void fuyiHeaderAliasesIncludeInstrumentCountSynonym() {
        String[][] aliases = BillColumnLayout.FUYI_EXTENDED_11COL.getHeaderAliases();
        boolean hasInstrumentAlias = false;
        for (String[] group : aliases) {
            for (String alias : group) {
                if ("单包内器械数量/把".equals(alias) || "器械数".equals(alias)) {
                    hasInstrumentAlias = true;
                }
            }
        }
        assertThat(hasInstrumentAlias).isTrue();
    }

    @Test
    void fixedDataColumnIndexForFuyiLayout() {
        BillColumnLayout layout = BillColumnLayout.FUYI_EXTENDED_11COL;
        assertThat(layout.fixedDataColumnIndex("单价")).isEqualTo(12);
        assertThat(layout.fixedDataColumnIndex("总价")).isEqualTo(13);
        assertThat(BillColumnLayout.STANDARD_8COL.fixedDataColumnIndex("单价")).isNull();
    }
}
