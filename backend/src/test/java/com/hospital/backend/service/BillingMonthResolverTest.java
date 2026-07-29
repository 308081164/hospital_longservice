package com.hospital.backend.service;

import com.hospital.backend.entity.HospitalReconciliationJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BillingMonthResolverTest {

    @Test
    void resolvesFromSixMonthFileNameForFuyi() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSourceFileName("6月__附一6月账单__附一6月账单.xlsx");
        job.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));

        assertThat(BillingMonthResolver.resolve(job)).isEqualTo("2026-06");
    }

    @Test
    void resolvesCrossMonthRangeEndMonthForShiwu() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSourceDateRange("2026年5月9日-2026年6月8日");

        assertThat(BillingMonthResolver.resolveFromDateRange(job.getSourceDateRange())).isEqualTo("2026-06");
    }

    @Test
    void resolvesIsoPrefixFromSourceDateRange() {
        assertThat(BillingMonthResolver.resolveFromDateRange("2026-06-01至2026-06-30"))
                .isEqualTo("2026-06");
    }

    @Test
    void resolvesMidMonthPeriodStartMonthForHit() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setSourceDateRange("从:2026/6/15 00:00:00 至: 2026/7/14 23:59:59.999");

        assertThat(BillingMonthResolver.resolveFromDateRange(job.getSourceDateRange())).isEqualTo("2026-06");
        assertThat(BillingMonthResolver.resolve(job)).isEqualTo("2026-06");
    }

    @Test
    void fallsBackToCreatedAtWhenNoHints() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setCreatedAt(LocalDateTime.of(2026, 6, 15, 0, 0));

        assertThat(BillingMonthResolver.resolve(job)).isEqualTo("2026-06");
    }
}
