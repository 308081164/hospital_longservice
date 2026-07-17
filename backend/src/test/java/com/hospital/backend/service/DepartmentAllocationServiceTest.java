package com.hospital.backend.service;

import com.hospital.backend.allocation.AllocationConfig;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.service.impl.DepartmentAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentAllocationServiceTest {

    private DepartmentAllocationServiceImpl service;
    private AllocationConfig config;

    @BeforeEach
    void setUp() {
        service = new DepartmentAllocationServiceImpl(null, null, null, null, null);
        config = new AllocationConfig();
    }

    @Test
    void matchesAdjustmentKeywordForDianzuan() {
        assertThat(service.matchesAdjustmentKeyword("骨科电钻包", config)).isTrue();
        assertThat(service.matchesAdjustmentKeyword("普通器械包", config)).isFalse();
    }

    @Test
    void matchRosterInTextFindsLongestDoctorName() {
        List<RosterEntry> roster = List.of(
                roster("张", "骨一科"),
                roster("张三", "骨二科")
        );
        Optional<RosterEntry> match = service.matchRosterInText("张三医生专用包", roster);
        assertThat(match).isPresent();
        assertThat(match.get().getDepartment()).isEqualTo("骨二科");
    }

    @Test
    void computeAllocationSeparatesFeeAdjustmentFromOperatingRoom() {
        List<HospitalReconciliationRow> rows = List.of(
                row(1L, "手术室", "电钻工具包", 500.0),
                row(2L, "手术室", "张三专用包", 300.0),
                row(3L, "骨一科", "常规包", 200.0)
        );
        List<RosterEntry> roster = List.of(roster("张三", "骨三科"));

        AllocationResult result = service.computeAllocation(
                1L, 10L, rows, roster, List.of(), config, 50.0);

        assertThat(result.getAdjustmentLines()).hasSize(1);
        assertThat(result.getAdjustmentLines().get(0).getAmount()).isEqualTo(500.0);
        assertThat(result.getAllocatedLines()).anyMatch(l -> "骨三科".equals(l.getTargetSheetName()));
        assertThat(result.getExternalInstrumentTotal()).isEqualTo(0);
        assertThat(result.getLogisticsTotal()).isEqualTo(50.0);
    }

    @Test
    void buildRosterHintsReturnsSuggestedDepartment() {
        List<HospitalReconciliationRow> rows = List.of(row(1L, "手术室", "李四手术包", 100.0));
        List<RosterEntry> roster = List.of(roster("李四", "普外科"));

        List<AllocationResult.RosterMatchHint> hints =
                service.buildRosterHints(1L, rows, roster);

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0).getSuggestedDepartment()).isEqualTo("普外科");
    }

    private RosterEntry roster(String doctor, String department) {
        RosterEntry entry = new RosterEntry();
        entry.setDoctorName(doctor);
        entry.setDepartment(department);
        entry.setIsActive(true);
        return entry;
    }

    private HospitalReconciliationRow row(Long id, String sheet, String packName, double amount) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setId(id);
        row.setSheetName(sheet);
        row.setPackName(packName);
        row.setPackCount(1);
        row.setInstrumentCount(10);
        row.setCorrectedTotalPrice(amount);
        row.setRowNumber(id.intValue());
        return row;
    }
}
