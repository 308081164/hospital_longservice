package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationExportRowFilterTest {

    private final ReconciliationExportRowFilter filter = new ReconciliationExportRowFilter();

    @Test
    void excludesUrgentRowsFromBillExport() {
        HospitalReconciliationRow urgent = new HospitalReconciliationRow();
        urgent.setPackName("剖宫包");
        urgent.setCorrectedTotalPrice(170.5);
        urgent.setIsUrgent(true);
        assertThat(filter.shouldIncludeForExport("HRB-HSZ", urgent)).isFalse();
    }

    @Test
    void excludesUrgentSheetRows() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("测试包");
        row.setCorrectedTotalPrice(12.0);
        row.setSheetName("加急");
        assertThat(filter.shouldIncludeForExport("HRB-HSZ", row)).isFalse();
    }

    @Test
    void excludesMainSheetRowsForUrgentOrdersOnHrbHsz() {
        HospitalReconciliationRow urgentSheet = new HospitalReconciliationRow();
        urgentSheet.setOrderNo("1612610");
        urgentSheet.setSheetName("加急");
        urgentSheet.setPackName("剖宫包□");
        urgentSheet.setCorrectedTotalPrice(1705.0);

        HospitalReconciliationRow mainSheet = new HospitalReconciliationRow();
        mainSheet.setOrderNo("1612610");
        mainSheet.setSheetName("账单");
        mainSheet.setPackName("剖宫包□");
        mainSheet.setCorrectedTotalPrice(1705.0);

        List<HospitalReconciliationRow> filtered =
                filter.apply("HRB-HSZ", List.of(urgentSheet, mainSheet));

        assertThat(filtered).isEmpty();
    }

    @Test
    void excludesShengYyRentalAndBeihuoRows() {
        HospitalReconciliationRow rental = new HospitalReconciliationRow();
        rental.setPackName("上肢器械（国药科学）1/4");
        rental.setType("骨科租赁器械包-带植入物");
        rental.setCorrectedTotalPrice(215.7);

        HospitalReconciliationRow beihuo = new HospitalReconciliationRow();
        beihuo.setSheetName("手术室（备货）");
        beihuo.setPackName("钛缆器械（国药伊春）");
        beihuo.setCorrectedTotalPrice(19.8);

        HospitalReconciliationRow regular = new HospitalReconciliationRow();
        regular.setSheetName("急诊科");
        regular.setPackName("克氏钳-1/双/z1029");
        regular.setCorrectedTotalPrice(9.9);

        List<HospitalReconciliationRow> filtered =
                filter.apply("SHENG-YY-XF", List.of(rental, beihuo, regular));

        assertThat(filtered).containsExactly(regular);
    }

    @Test
    void excludesHrbHszHeuristicUrgentRowsOnMainSheet() {
        HospitalReconciliationRow caige = urgentStyleRow("1612610", "剖宫包□", 10, 1705.0);
        HospitalReconciliationRow cup = urgentStyleRow("1612610", "麻杯1换药碗2弯盘1/W6050", 10, 220.0);
        HospitalReconciliationRow regular = urgentStyleRow("1610933", "剖宫包□", 1, 170.5);
        HospitalReconciliationRow regularCup = urgentStyleRow("1610933", "麻杯1换药碗2弯盘1/W6050", 1, 22.0);

        List<HospitalReconciliationRow> filtered =
                filter.apply("HRB-HSZ", List.of(caige, cup, regular, regularCup));

        assertThat(filtered).containsExactly(regular, regularCup);
    }

    @Test
    void keepsHrbHszSplitUrgentOrderAccessoryRows() {
        HospitalReconciliationRow scrape = urgentStyleRow("1614138", "刮宫包（10件）", 4, 220.0);
        HospitalReconciliationRow caige = urgentStyleRow("1614138", "剖宫包□", 8, 1364.0);
        HospitalReconciliationRow cup = urgentStyleRow("1614138", "麻杯1换药碗2弯盘1/W6050", 8, 176.0);
        HospitalReconciliationRow forceps = urgentStyleRow("1614138", "持物钳罐-2/W6050", 3, 49.5);
        HospitalReconciliationRow tray = urgentStyleRow("1614138", "弯盘1碗1/W6050", 1, 16.5);

        List<HospitalReconciliationRow> filtered = filter.apply(
                "HRB-HSZ", List.of(scrape, caige, cup, forceps, tray));

        assertThat(filtered).containsExactly(forceps, tray);
    }

    private static HospitalReconciliationRow urgentStyleRow(
            String orderNo, String packName, int packCount, double total) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setOrderNo(orderNo);
        row.setSheetName("手术室");
        row.setPackName(packName);
        row.setPackCount(packCount);
        row.setCorrectedTotalPrice(total);
        return row;
    }

    @Test
    void excludesSettlementSheetRows() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("测试包");
        row.setCorrectedTotalPrice(12.0);
        row.setSheetName("结款函");
        assertThat(filter.shouldIncludeForExport("HRB-HSZ", row)).isFalse();
    }

    @Test
    void keepsPricedRowsForShengYy() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("测试包");
        row.setCorrectedTotalPrice(12.0);
        assertThat(filter.shouldIncludeForExport("SHENG-YY-NG", row)).isTrue();
    }

    @Test
    void excludesBlankPackNameAndZeroTotalsForShengYy() {
        HospitalReconciliationRow blank = new HospitalReconciliationRow();
        blank.setPackName("  ");
        blank.setTotalPrice(0.0);
        assertThat(filter.shouldIncludeForExport("SHENG-YY-NG", blank)).isFalse();

        HospitalReconciliationRow zero = new HospitalReconciliationRow();
        zero.setPackName("占位行");
        zero.setTotalPrice(0.0);
        zero.setCorrectedTotalPrice(0.0);
        assertThat(filter.shouldIncludeForExport("SHENG-YY-NG", zero)).isFalse();
    }

    @Test
    void keepsZeroPriceOverrideRows() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("仅记录包");
        row.setTotalPrice(0.0);
        row.setPricingRule("0元仅记录");
        assertThat(filter.shouldIncludeForExport("SHENG-YY-NG", row)).isTrue();
    }

    @Test
    void noOpForNonShengYyCustomer() {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setPackName("x");
        row.setTotalPrice(0.0);
        assertThat(filter.apply("HRB-WY", java.util.List.of(row))).containsExactly(row);
    }
}
