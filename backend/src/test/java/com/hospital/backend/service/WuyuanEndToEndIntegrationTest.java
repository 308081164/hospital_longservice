package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.allocation.AllocationConfig;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.export.SheetOrchestrator;
import com.hospital.backend.service.impl.DepartmentAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7-21：市五院端到端分配 + 多 Sheet 导出骨架集成测试（无 DB）。
 */
class WuyuanEndToEndIntegrationTest {

    private DepartmentAllocationServiceImpl allocationService;
    private SheetOrchestrator sheetOrchestrator;
    private AllocationConfig config;

    @BeforeEach
    void setUp() {
        allocationService = new DepartmentAllocationServiceImpl(null, null, null, null, null);
        sheetOrchestrator = new SheetOrchestrator();
        config = new AllocationConfig();
    }

    @Test
    void fullPipeline_allocationBalanced_andOrchestratedWorkbookGenerated() throws Exception {
        List<HospitalReconciliationRow> rows = List.of(
                row(1L, "手术室", "电钻工具包", 500.0, 1),
                row(2L, "手术室", "张三专用包", 300.0, 2),
                row(3L, "骨一科", "常规包", 200.0, 3)
        );
        List<RosterEntry> roster = List.of(roster("张三", "骨三科"));
        List<ExternalInstrument> external = List.of(external("骨三科", "EXT-001", "外来关节镜", 150.0));

        AllocationResult allocation = allocationService.computeAllocation(
                100L, 10L, rows, roster, external, config, 50.0);

        assertThat(allocation.isBalanced()).isTrue();
        assertThat(allocation.getAdjustmentLines()).hasSize(1);
        assertThat(allocation.getExternalInstrumentTotal()).isEqualTo(150.0);
        assertThat(allocation.getLogisticsTotal()).isEqualTo(50.0);
        assertThat(allocation.getDepartmentSummaries()).isNotEmpty();

        byte[] workbook = sheetOrchestrator.buildOrchestratedWorkbook(
                "哈尔滨市第五医院", rows, allocation, external);

        assertThat(workbook).isNotEmpty();
        assertThat(workbook.length).isGreaterThan(512);
    }

    @Test
    void operatingRoomNetPlusAdjustmentsEqualsOriginalTotal() {
        List<HospitalReconciliationRow> rows = List.of(
                row(1L, "手术室", "电钻包", 400.0, 1),
                row(2L, "手术室", "常规包", 600.0, 2)
        );

        AllocationResult result = allocationService.computeAllocation(
                1L, 1L, rows, List.of(), List.of(), config, 0.0);

        assertThat(result.getOriginalGrandTotal()).isEqualTo(1000.0);
        assertThat(result.getAdjustmentTotal()).isEqualTo(400.0);
        assertThat(result.isBalanced()).isTrue();
    }

    private HospitalReconciliationRow row(Long id, String sheet, String packName, double total, int rowNum) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setId(id);
        row.setSheetName(sheet);
        row.setPackName(packName);
        row.setCorrectedTotalPrice(total);
        row.setTotalPrice(total);
        row.setRowNumber(rowNum);
        row.setPackCount(1);
        row.setInstrumentCount(1);
        return row;
    }

    private RosterEntry roster(String doctor, String department) {
        RosterEntry entry = new RosterEntry();
        entry.setDoctorName(doctor);
        entry.setDepartment(department);
        entry.setIsActive(true);
        return entry;
    }

    private ExternalInstrument external(String dept, String categoryNo, String packName, double total) {
        ExternalInstrument inst = new ExternalInstrument();
        inst.setDepartment(dept);
        inst.setCategoryNo(categoryNo);
        inst.setPackName(packName);
        inst.setTotalAmount(BigDecimal.valueOf(total));
        inst.setUnitPrice(BigDecimal.valueOf(total));
        inst.setPackCount(1);
        return inst;
    }
}
