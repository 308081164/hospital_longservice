package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.service.DailySplitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DailySplitServiceImpl implements DailySplitService {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;

    @Override
    public Result<Map<String, Object>> splitJobByDate(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "对账任务不存在");
        }

        List<HospitalReconciliationRow> rows = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        Map<String, DailySummary> dailyMap = new TreeMap<>();

        for (HospitalReconciliationRow row : rows) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            String date = row.getDeliveryDate() != null && !row.getDeliveryDate().isBlank()
                    ? row.getDeliveryDate()
                    : "未知日期";
            dailyMap.computeIfAbsent(date, d -> new DailySummary()).add(row);
        }

        List<Map<String, Object>> dailyEntries = new ArrayList<>();
        double dailyTotal = 0;
        int dailyPackTotal = 0;
        for (Map.Entry<String, DailySummary> entry : dailyMap.entrySet()) {
            DailySummary summary = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deliveryDate", entry.getKey());
            item.put("rowCount", summary.rowCount);
            item.put("packCount", summary.packCount);
            item.put("originalTotal", round2(summary.originalTotal));
            item.put("correctedTotal", round2(summary.correctedTotal));
            dailyEntries.add(item);
            dailyTotal += summary.correctedTotal;
            dailyPackTotal += summary.packCount;
        }

        double monthlyCorrected = job.getCorrectedTotalPrice() != null
                ? job.getCorrectedTotalPrice().doubleValue()
                : 0;
        boolean reconciled = Math.abs(dailyTotal - monthlyCorrected) <= 0.01;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("hospitalName", job.getHospitalName());
        result.put("dailyEntries", dailyEntries);
        result.put("dailyCorrectedSum", round2(dailyTotal));
        result.put("monthlyCorrectedTotal", round2(monthlyCorrected));
        result.put("dailyPackSum", dailyPackTotal);
        result.put("reconciled", reconciled);
        result.put("reconciliationDelta", round2(dailyTotal - monthlyCorrected));
        result.put("templateType", "daily");
        return Result.success(result);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class DailySummary {
        int rowCount;
        int packCount;
        double originalTotal;
        double correctedTotal;

        void add(HospitalReconciliationRow row) {
            rowCount++;
            packCount += row.getPackCount() != null ? row.getPackCount() : 0;
            if (row.getTotalPrice() != null) {
                originalTotal += row.getTotalPrice();
            }
            if (row.getCorrectedTotalPrice() != null) {
                correctedTotal += row.getCorrectedTotalPrice();
            } else if (row.getTotalPrice() != null) {
                correctedTotal += row.getTotalPrice();
            }
        }
    }
}
