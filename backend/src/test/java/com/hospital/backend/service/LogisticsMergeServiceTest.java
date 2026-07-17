package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsMergeServiceTest {

    @Test
    void mergeSameDayCrossCustomer_splitsFeeEquallyWhenTwoCustomersShareDate() {
        LocalDate day = LocalDate.of(2026, 7, 3);
        List<Long> group = List.of(1L, 2L);
        List<LogisticsMergeService.CustomerDayActivity> activities = List.of(
                new LogisticsMergeService.CustomerDayActivity(1L, day),
                new LogisticsMergeService.CustomerDayActivity(2L, day));

        LogisticsMergeService.MergeResult result = LogisticsMergeService.mergeSameDayCrossCustomer(
                50.0, 1L, group, activities, Map.of());

        assertThat(result.totalFeeForCustomer()).isEqualTo(25.0);
        assertThat(result.dayDetails()).hasSize(1);
        assertThat(result.dayDetails().get(0).activeCustomerCount()).isEqualTo(2);
    }

    @Test
    void mergeSameDayCrossCustomer_chargesFullFeeWhenOnlyOneCustomerActive() {
        LocalDate day = LocalDate.of(2026, 7, 4);
        List<Long> group = List.of(1L, 2L);
        List<LogisticsMergeService.CustomerDayActivity> activities = List.of(
                new LogisticsMergeService.CustomerDayActivity(1L, day));

        LogisticsMergeService.MergeResult result = LogisticsMergeService.mergeSameDayCrossCustomer(
                50.0, 1L, group, activities, Map.of());

        assertThat(result.totalFeeForCustomer()).isEqualTo(50.0);
    }
}
