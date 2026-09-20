package com.hospital.backend.export;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPeriodFormatterTest {

    @Test
    void parsesIsoDateRangeFromBillPeriod() {
        Optional<SettlementPeriodFormatter.BillingPeriod> period = SettlementPeriodFormatter.parse(
                "从:2026/4/26 00:00:00 至: 2026/5/25 23:59:59.999");

        assertThat(period).isPresent();
        assertThat(period.get().start()).isEqualTo(LocalDate.of(2026, 4, 26));
        assertThat(period.get().end()).isEqualTo(LocalDate.of(2026, 5, 25));
    }

    @Test
    void formatsSettlementIntroAndClosingDate() {
        SettlementPeriodFormatter.BillingPeriod period = new SettlementPeriodFormatter.BillingPeriod(
                LocalDate.of(2026, 4, 26),
                LocalDate.of(2026, 5, 25));

        assertThat(SettlementPeriodFormatter.formatSettlementIntro(period))
                .isEqualTo("从:2026年4月26日  至: 2026年5月25日 灭菌费用总清单如下：");
        assertThat(SettlementPeriodFormatter.formatClosingDate(period)).isEqualTo("2026年5月25日");
    }

    @Test
    void buildTitleIncludesHospitalName() {
        assertThat(SettlementPeriodFormatter.buildTitle("黑龙江菁华上德生殖妇产医院", "标准灭菌计费规则"))
                .isEqualTo("黑龙江菁华上德生殖妇产医院标准灭菌计费规则结款通知函");
    }

    @Test
    void replaceClosingDateUsesPeriodEnd() {
        SettlementPeriodFormatter.BillingPeriod period = new SettlementPeriodFormatter.BillingPeriod(
                LocalDate.of(2026, 4, 26),
                LocalDate.of(2026, 5, 25));

        assertThat(SettlementPeriodFormatter.replaceClosingDate("黑龙江省铂康医疗灭菌有限公司\n2026年5月13日", period))
                .isEqualTo("黑龙江省铂康医疗灭菌有限公司\n2026年5月25日");
    }
}
