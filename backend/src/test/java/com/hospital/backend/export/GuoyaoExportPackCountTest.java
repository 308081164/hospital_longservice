package com.hospital.backend.export;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国药 export 包数与导入一致（客户反馈 2026-08）。
 */
class GuoyaoExportPackCountTest {

    private final BillExportRowGrouper grouper = new BillExportRowGrouper();
    private final GuoyaoQuantityAlgorithm algorithm = new GuoyaoQuantityAlgorithm();

    @Test
    void preservesSingleRowWithPackCountTwo() {
        HospitalReconciliationRow row = guoyaoRow(
                "1618564", "20303908", "病理钳（低温）-1/z1560", "额外包(低温)", 2);

        List<HospitalReconciliationRow> exported = guoyaoExportPipeline(List.of(row));

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).getPackCount()).isEqualTo(2);
    }

    @Test
    void preservesDistinctPackCountsOnSameOrder() {
        HospitalReconciliationRow pouPack = guoyaoRow(
                "1619108", "20009043", "剖包-3/w6050", "器械包(ZSD)", 3);
        HospitalReconciliationRow cotton = guoyaoRow(
                "1619108", "20009169", "棉球大/z2032", "敷料包(纸塑代)", 1);

        List<HospitalReconciliationRow> exported =
                guoyaoExportPipeline(List.of(pouPack, cotton));

        assertThat(exported).hasSize(2);
        assertThat(findPackCount(exported, "剖包-3/w6050")).isEqualTo(3);
        assertThat(findPackCount(exported, "棉球大/z2032")).isEqualTo(1);
    }

    @Test
    void preservesDisinfectionJarAndScissorsPackCounts() {
        HospitalReconciliationRow jar = guoyaoRow(
                "1623123", "20012999", "消毒缸-3/W7050", "额外包(无纺布)", 2);
        HospitalReconciliationRow scissors = guoyaoRow(
                "1623183", "20344020", "病理剪-1/z1560", "额外包(低温)", 1);

        List<HospitalReconciliationRow> exported = guoyaoExportPipeline(List.of(jar, scissors));

        assertThat(exported).hasSize(2);
        assertThat(findPackCount(exported, "消毒缸-3/W7050")).isEqualTo(2);
        assertThat(findPackCount(exported, "病理剪-1/z1560")).isEqualTo(1);
    }

    @Test
    void doesNotMergeRowsWithSamePackNameButDifferentCategoryNo() {
        HospitalReconciliationRow a = guoyaoRow("1619108", "20009043", "测试包", "器械包(ZSD)", 2);
        HospitalReconciliationRow b = guoyaoRow("1619108", "20009169", "测试包", "器械包(ZSD)", 3);

        List<HospitalReconciliationRow> aggregated = grouper.aggregateGuoyaoDuplicateRows(List.of(a, b));

        assertThat(aggregated).hasSize(2);
        assertThat(aggregated.stream().mapToInt(HospitalReconciliationRow::getPackCount).sum()).isEqualTo(5);
    }

    @Test
    void aggregatesExactDuplicateRowsOnly() {
        HospitalReconciliationRow r1 = guoyaoRow("1574303", "20303908", "止血钳-1/z1029", "额外包(低温)", 5);
        HospitalReconciliationRow r2 = guoyaoRow("1574303", "20303908", "止血钳-1/z1029", "额外包(低温)", 5);

        List<HospitalReconciliationRow> aggregated = grouper.aggregateGuoyaoDuplicateRows(List.of(r1, r2));

        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0).getPackCount()).isEqualTo(10);
    }

    /** 与 {@link ReconciliationExportDataLoader} 国药分支一致（不含 splitGuoyaoPlatinumRows）。 */
    private List<HospitalReconciliationRow> guoyaoExportPipeline(List<HospitalReconciliationRow> rows) {
        List<HospitalReconciliationRow> working = grouper.aggregateGuoyaoDuplicateRows(rows);
        working.forEach(algorithm::applyToRow);
        return working;
    }

    private static Integer findPackCount(List<HospitalReconciliationRow> rows, String packName) {
        return rows.stream()
                .filter(r -> packName.equals(r.getPackName()))
                .map(HospitalReconciliationRow::getPackCount)
                .findFirst()
                .orElse(null);
    }

    private static HospitalReconciliationRow guoyaoRow(
            String orderNo, String categoryNo, String packName, String type, int packCount) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setOrderNo(orderNo);
        row.setCategoryNo(categoryNo);
        row.setPackName(packName);
        row.setType(type);
        row.setDeliveryDate("2026-07-08");
        row.setPackCount(packCount);
        row.setUnitPrice(25.0);
        row.setTotalPrice(25.0 * packCount);
        row.setCorrectedTotalPrice(25.0 * packCount);
        return row;
    }
}
