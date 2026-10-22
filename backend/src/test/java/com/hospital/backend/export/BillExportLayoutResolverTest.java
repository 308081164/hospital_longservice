package com.hospital.backend.export;

import com.hospital.backend.service.impl.HospitalReconciliationServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillExportLayoutResolverTest {

    private final BillExportLayoutResolver resolver = new BillExportLayoutResolver();

    @Test
    void deptSplit_overridesOomThreshold() {
        long threshold = HospitalReconciliationServiceImpl.BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD;
        // 附一单月常 >1000 行：显式 dept_split 不得退回合计单表
        assertTrue(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 30, threshold + 500));
        assertTrue(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 45, 2200));
    }

    @Test
    void auto_aboveThresholdStillCombined_butDeptSplitDoesNot() {
        long threshold = HospitalReconciliationServiceImpl.BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD;
        assertFalse(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_AUTO, 40, threshold + 200));
        assertTrue(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 40, threshold + 200));
    }

    @Test
    void combined_forcesSingleSheetEvenWithManySheets() {
        assertFalse(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_COMBINED, 30, 2000));
    }

    @Test
    void auto_usesOomGuard() {
        long threshold = HospitalReconciliationServiceImpl.BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD;
        assertFalse(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_AUTO, 5, threshold + 1));
        assertTrue(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_AUTO, 5, threshold));
    }

    @Test
    void preferProgrammaticTemplate_onlyForDeptSplitLargeJobs() {
        assertTrue(resolver.preferProgrammaticTemplate(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 1500));
        assertFalse(resolver.preferProgrammaticTemplate(
                BillExportLayoutResolver.LAYOUT_COMBINED, 1500));
        assertFalse(resolver.preferProgrammaticTemplate(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 500));
    }

    @Test
    void buildExportProfileLabel_specialDeptSplit() {
        String label = resolver.buildExportProfileLabel(true, BillExportLayoutResolver.LAYOUT_DEPT_SPLIT);
        assertTrue(label.contains("特色导出"));
        assertTrue(label.contains("分科室"));
    }

    @Test
    void resolveBillColumnLayout_fromSeedConfig() {
        var config = new com.hospital.backend.export.model.ColumnMappingConfig();
        config.setBillColumnLayout("fuyi_extended_11col");
        assertTrue(resolver.resolveBillColumnLayout(config).isExtended());
        assertFalse(resolver.resolveBillColumnLayout((com.hospital.backend.export.model.ColumnMappingConfig) null).isExtended());
        assertFalse(resolver.resolveBillColumnLayout("standard_8col").isExtended());
    }
}
