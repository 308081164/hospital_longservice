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
        assertTrue(resolver.useDeptSplitWorkbook(
                BillExportLayoutResolver.LAYOUT_DEPT_SPLIT, 30, threshold + 500));
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
}
