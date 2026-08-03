package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillExportRowGrouperTest {

    private final BillExportRowGrouper grouper = new BillExportRowGrouper();

    @Test
    void mergesShengYySubPackSuffixRows() {
        HospitalReconciliationRow r1 = row("1605293", "膝关节镜器械 1/5", 100.0);
        HospitalReconciliationRow r2 = row("1605293", "膝关节镜器械 2/5", 120.0);
        HospitalReconciliationRow r3 = row("1605293", "其他包", 50.0);

        List<HospitalReconciliationRow> merged =
                grouper.mergeSubPackSuffixRows(List.of(r1, r2, r3));

        assertThat(merged).hasSize(2);
        HospitalReconciliationRow kit = merged.stream()
                .filter(r -> "膝关节镜器械".equals(r.getPackName()))
                .findFirst()
                .orElseThrow();
        assertThat(kit.getCorrectedTotalPrice()).isEqualTo(220.0);
    }

    @Test
    void excludesEryySbKitComponentSlashRows() {
        HospitalReconciliationRow kit = row("1614878", "宫腔镜包", 200.0);
        HospitalReconciliationRow component = row("1614878", "电切镜/电切环", 30.0);
        HospitalReconciliationRow partRow = row("1612746", "组件-1/Z3040", 24.5);
        HospitalReconciliationRow instrumentPart = row("1612746", "持针器-1/Z1026", 23.1);
        HospitalReconciliationRow strozScope = row("1615345", "STROZ腹腔镜-1（30度）/Z2060", 19.6);

        List<HospitalReconciliationRow> filtered =
                grouper.excludeKitComponentRows(List.of(kit, component, partRow, instrumentPart, strozScope));

        assertThat(filtered).extracting(HospitalReconciliationRow::getPackName)
                .containsExactly("宫腔镜包", "组件-1/Z3040", "持针器-1/Z1026", "STROZ腹腔镜-1（30度）/Z2060");
    }

    @Test
    void mergesShengYySubPackSuffixWithoutSpace() {
        HospitalReconciliationRow r1 = row("1606875", "上肢器械（国药科学）1/4", 100.0);
        HospitalReconciliationRow r2 = row("1606875", "上肢器械（国药科学）2/4", 120.0);

        List<HospitalReconciliationRow> merged = grouper.mergeSubPackSuffixRows(List.of(r1, r2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getPackName()).isEqualTo("上肢器械（国药科学）");
        assertThat(merged.get(0).getCorrectedTotalPrice()).isEqualTo(220.0);
    }

    @Test
    void mergesShengYySubPackSuffixWithVendorSuffix() {
        HospitalReconciliationRow r1 = row("1610325", "微创钉棒器械  1/4（国药科学）", 314.7);
        HospitalReconciliationRow r2 = row("1610325", "微创钉棒器械  3/4（国药科学）", 308.1);

        List<HospitalReconciliationRow> merged = grouper.mergeSubPackSuffixRows(List.of(r1, r2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getPackName()).isEqualTo("微创钉棒器械（国药科学）");
        assertThat(merged.get(0).getCorrectedTotalPrice()).isEqualTo(622.8);
    }

    @Test
    void mergesHrbHszSubPackSuffixRows() {
        HospitalReconciliationRow r1 = row("1610864", "刘滨利-全髋关节置换  1/7", 93.5);
        HospitalReconciliationRow r2 = row("1610864", "刘滨利-全髋关节置换  2/7", 16.5);

        List<HospitalReconciliationRow> merged = grouper.apply("HRB-HSZ", List.of(r1, r2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getPackName()).isEqualTo("刘滨利-全髋关节置换");
        assertThat(merged.get(0).getCorrectedTotalPrice()).isEqualTo(110.0);
    }

    @Test
    void dedupesExactDuplicateRowsForFoldCustomers() {
        HospitalReconciliationRow r1 = row("1611875", "排针20", 94.5);
        r1.setPackCount(7);
        r1.setExpectedUnitPrice(13.5);
        HospitalReconciliationRow r2 = row("1611875", "排针20", 94.5);
        r2.setPackCount(7);
        r2.setExpectedUnitPrice(13.5);

        List<HospitalReconciliationRow> deduped =
                grouper.apply("ZUYAN-NG", List.of(r1, r2));

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).getCorrectedTotalPrice()).isEqualTo(94.5);
    }

    @Test
    void splitsGuoyaoRowsWithPackCountGreaterThanOne() {
        HospitalReconciliationRow row = row("1574303", "止血钳-1/z1029", 55.0);
        row.setPackCount(10);
        row.setExpectedUnitPrice(5.5);
        row.setInstrumentCount(100);

        List<HospitalReconciliationRow> split = grouper.splitGuoyaoPlatinumRows(List.of(row));

        assertThat(split).hasSize(10);
        assertThat(split).allMatch(r -> r.getPackCount() == 1);
        assertThat(split).allMatch(r -> r.getCorrectedTotalPrice() == 5.5);
        assertThat(split.stream().mapToInt(HospitalReconciliationRow::getInstrumentCount).sum()).isEqualTo(100);
    }

    @Test
    void aggregatesGuoyaoDuplicatesWithoutSplitting() {
        HospitalReconciliationRow r1 = row("1574303", "止血钳-1/z1029", 27.5);
        r1.setPackCount(5);
        r1.setCategoryNo("20303908");
        HospitalReconciliationRow r2 = row("1574303", "止血钳-1/z1029", 27.5);
        r2.setPackCount(5);
        r2.setCategoryNo("20303908");

        List<HospitalReconciliationRow> aggregated = grouper.aggregateGuoyaoDuplicateRows(List.of(r1, r2));

        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0).getPackCount()).isEqualTo(10);
    }

    private static HospitalReconciliationRow row(String orderNo, String packName, double total) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setOrderNo(orderNo);
        row.setPackName(packName);
        row.setPackCount(1);
        row.setCorrectedTotalPrice(total);
        return row;
    }
}
