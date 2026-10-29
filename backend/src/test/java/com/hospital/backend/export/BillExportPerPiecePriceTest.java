package com.hospital.backend.export;

import com.hospital.backend.dto.request.hospital.BillRowItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 导出 11 列：K「单包内器械数量/把」与 L「单价（把）」的件数口径。
 * 2026-10-29 客户反馈：多包行 K 写成了整行合计，L 也被重复除一次包数。
 */
class BillExportPerPiecePriceTest {

    private BillRowItem row(int packCount, int instrumentCount, double totalPrice, double unitPrice) {
        BillRowItem item = new BillRowItem();
        item.setPackCount(packCount);
        item.setInstrumentCount(instrumentCount);
        item.setTotalPrice(totalPrice);
        item.setUnitPrice(unitPrice);
        return item;
    }

    @Test
    void multiPackRowUsesPerPackInstrumentCount() {
        // 器械数为「包内件数」：包数 2、每包 6 把、总价 105.60 → 8.80
        BillRowItem item = row(2, 6, 105.60, 52.80);
        assertThat(BillExportPriceResolver.resolvePerPiecePrice(item))
                .isCloseTo(8.80, within(0.02));
    }

    @Test
    void singlePackRowKeepsDirectDivision() {
        // 包数 1、器械数 4、总价 70.32 → 17.58
        BillRowItem item = row(1, 4, 70.32, 70.32);
        assertThat(BillExportPriceResolver.resolvePerPiecePrice(item))
                .isCloseTo(17.58, within(0.02));
    }
}
