package com.hospital.backend.service.impl;

import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySplitServiceImplTest {

    @Mock
    private HospitalReconciliationJobMapper jobMapper;

    @Mock
    private HospitalReconciliationRowMapper rowMapper;

    @InjectMocks
    private DailySplitServiceImpl dailySplitService;

    @Test
    void splitJobByDateReconcilesToMonthlyTotal() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setId(1L);
        job.setHospitalName("远东心脑血管");
        job.setCorrectedTotalPrice(300.00);
        when(jobMapper.selectById(1L)).thenReturn(job);

        HospitalReconciliationRow r1 = row("2026-06-01", 100.0, 2);
        HospitalReconciliationRow r2 = row("2026-06-02", 200.0, 3);
        when(rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(1L)).thenReturn(List.of(r1, r2));

        var result = dailySplitService.splitJobByDate(1L);
        assertThat(result.getCode()).isEqualTo(200);
        Map<String, Object> data = result.getData();
        assertThat(data.get("reconciled")).isEqualTo(true);
        assertThat(data.get("dailyCorrectedSum")).isEqualTo(300.0);
        assertThat(((List<?>) data.get("dailyEntries"))).hasSize(2);
    }

    private static HospitalReconciliationRow row(String date, double corrected, int packs) {
        HospitalReconciliationRow row = new HospitalReconciliationRow();
        row.setDeliveryDate(date);
        row.setCorrectedTotalPrice(corrected);
        row.setPackCount(packs);
        row.setStatus("unchanged");
        return row;
    }
}
