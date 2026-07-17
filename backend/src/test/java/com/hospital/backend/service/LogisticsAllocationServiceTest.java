package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsAllocationServiceTest {

    @Test
    void allocateByDeptRatio_splitsProportionallyAndSumsToTotal() {
        List<Map<String, Object>> rows = List.of(
                Map.of("sheetName", "手术室", "correctedTotalPrice", 600.0),
                Map.of("sheetName", "内科", "correctedTotalPrice", 400.0));

        LogisticsAllocationService.AllocationResult result =
                LogisticsAllocationService.allocateByDeptRatio(100.0, rows, List.of());

        assertThat(result.departments()).hasSize(2);
        assertThat(result.allocatedSum()).isEqualTo(100.0);
        assertThat(result.departments().get(0).department()).isEqualTo("手术室");
        assertThat(result.departments().get(0).allocatedFee()).isEqualTo(60.0);
        assertThat(result.departments().get(1).allocatedFee()).isEqualTo(40.0);
    }

    @Test
    void allocateByDeptRatio_excludesConfiguredDepartments() {
        List<Map<String, Object>> rows = List.of(
                Map.of("sheetName", "供应中心", "correctedTotalPrice", 500.0),
                Map.of("sheetName", "手术室", "correctedTotalPrice", 500.0));

        LogisticsAllocationService.AllocationResult result =
                LogisticsAllocationService.allocateByDeptRatio(50.0, rows, List.of("供应中心"));

        assertThat(result.departments()).hasSize(1);
        assertThat(result.departments().get(0).department()).isEqualTo("手术室");
        assertThat(result.departments().get(0).allocatedFee()).isEqualTo(50.0);
    }
}
