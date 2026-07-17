package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.*;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.allocation.RunAllocationRequest;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.mapper.RosterEntryMapper;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.DepartmentAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentAllocationServiceImpl implements DepartmentAllocationService {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final RosterEntryMapper rosterEntryMapper;
    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final CustomerResolver customerResolver;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    @Transactional
    public Result<AllocationResult> runAllocation(Long jobId, RunAllocationRequest request) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "Reconciliation job not found: " + jobId);
        }

        Optional<Customer> customerOpt = customerResolver.resolveByName(job.getHospitalName());
        if (customerOpt.isEmpty()) {
            return Result.fail(400, "Cannot resolve customer for hospital: " + job.getHospitalName());
        }
        Long customerId = customerOpt.get().getId();

        AllocationConfig config = request != null && request.getConfig() != null
                ? request.getConfig()
                : new AllocationConfig();

        List<HospitalReconciliationRow> rows =
                rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        List<RosterEntry> roster = rosterEntryMapper.selectActiveByCustomerId(customerId);
        List<ExternalInstrument> externalRows = externalInstrumentMapper.selectByJobId(jobId);

        AllocationResult result = computeAllocation(
                jobId, customerId, rows, roster, externalRows, config, job.getLogisticsFee());

        try {
            job.setAllocationResult(objectMapper.writeValueAsString(result));
            jobMapper.updateAllocationResult(jobId, job.getAllocationResult());
        } catch (Exception e) {
            log.warn("Failed to persist allocation_result for job {}: {}", jobId, e.getMessage());
            return Result.fail(500, "Allocation computed but failed to save: " + e.getMessage());
        }

        return Result.success(result);
    }

    @Override
    public Result<AllocationResult> getAllocationResult(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "Reconciliation job not found: " + jobId);
        }
        if (job.getAllocationResult() == null || job.getAllocationResult().isBlank()) {
            return Result.fail(404, "No allocation result for job: " + jobId);
        }
        try {
            AllocationResult result = objectMapper.readValue(job.getAllocationResult(), AllocationResult.class);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(500, "Failed to parse allocation result: " + e.getMessage());
        }
    }

    public AllocationResult computeAllocation(
            Long jobId,
            Long customerId,
            List<HospitalReconciliationRow> rows,
            List<RosterEntry> roster,
            List<ExternalInstrument> externalRows,
            AllocationConfig config,
            Double logisticsFee) {

        AllocationResult result = new AllocationResult();
        result.setJobId(jobId);
        result.setCustomerId(customerId);
        result.setRosterHints(buildRosterHints(customerId, rows, roster));

        Map<String, DepartmentSheetSummary> summaryByDept = new LinkedHashMap<>();
        double originalTotal = 0;
        double adjustmentTotal = 0;

        for (HospitalReconciliationRow row : rows) {
            double amount = rowAmount(row);
            originalTotal += amount;

            String sheet = row.getSheetName() != null ? row.getSheetName() : "(默认)";
            String searchable = buildSearchText(row);

            if (isOperatingRoomSheet(sheet, config) && matchesAdjustmentKeyword(searchable, config)) {
                AllocatedLineItem adj = toAdjustmentLine(row, amount);
                result.getAdjustmentLines().add(adj);
                adjustmentTotal += amount;
                accumulateSummary(summaryByDept, "费用调整", "adjustment", row, amount, 0);
                continue;
            }

            if (isOperatingRoomSheet(sheet, config)) {
                Optional<RosterEntry> rosterMatch = matchRosterInText(searchable, roster);
                if (rosterMatch.isPresent()) {
                    RosterEntry entry = rosterMatch.get();
                    AllocatedLineItem line = toAllocatedLine(row, entry.getDepartment(), "roster", amount);
                    line.setMatchedDoctor(entry.getDoctorName());
                    line.setMatchedDepartment(entry.getDepartment());
                    line.setMatchReason("花名册命中医生: " + entry.getDoctorName());
                    result.getAllocatedLines().add(line);
                    String targetSheet = isLowTemperatureRow(row, config)
                            ? entry.getDepartment() + "低温"
                            : entry.getDepartment();
                    accumulateSummary(summaryByDept, targetSheet,
                            isLowTemperatureRow(row, config) ? "low_temp" : "regular",
                            row, amount, 0);
                    continue;
                }
            }

            if (isOperatingRoomSheet(sheet, config) && isLowTemperatureRow(row, config)) {
                String dept = resolveDepartmentPrefix(searchable, config);
                if (dept != null) {
                    AllocatedLineItem line = toAllocatedLine(row, dept + "低温", "low_temp", amount);
                    line.setMatchReason("低温拆分");
                    result.getAllocatedLines().add(line);
                    accumulateSummary(summaryByDept, dept + "低温", "low_temp", row, amount, 0);
                    continue;
                }
            }

            String prefixDept = resolveDepartmentPrefix(searchable, config);
            if (prefixDept != null && isOperatingRoomSheet(sheet, config)) {
                AllocatedLineItem line = toAllocatedLine(row, prefixDept, "dept_prefix", amount);
                line.setMatchedDepartment(prefixDept);
                line.setMatchReason("科室前缀匹配");
                result.getAllocatedLines().add(line);
                accumulateSummary(summaryByDept, prefixDept, "regular", row, amount, 0);
                continue;
            }

            accumulateSummary(summaryByDept, sheet, "source_sheet", row, amount, 0);
        }

        applySupplyRoomBorrow(summaryByDept, config, result);

        double externalTotal = externalRows.stream()
                .map(this::externalAmount)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        double logisticsTotal = logisticsFee != null ? logisticsFee : 0;
        double deptNet = summaryByDept.values().stream()
                .mapToDouble(DepartmentSheetSummary::getNetAmount)
                .sum();

        result.setOriginalGrandTotal(round2(originalTotal));
        result.setAdjustmentTotal(round2(adjustmentTotal));
        result.setExternalInstrumentTotal(round2(externalTotal));
        result.setLogisticsTotal(round2(logisticsTotal));
        result.setReconciledGrandTotal(round2(deptNet + externalTotal + logisticsTotal));
        result.setDepartmentSummaries(new ArrayList<>(summaryByDept.values()));

        Map<String, Double> priceSummary = new LinkedHashMap<>();
        priceSummary.put("常规账单", round2(originalTotal - adjustmentTotal));
        priceSummary.put("费用调整", round2(adjustmentTotal));
        priceSummary.put("外来器械", round2(externalTotal));
        priceSummary.put("物流费", round2(logisticsTotal));
        priceSummary.put("合计", result.getReconciledGrandTotal());
        result.setPriceSummaryByCategory(priceSummary);

        double expected = round2(originalTotal + externalTotal + logisticsTotal);
        result.setBalanced(Math.abs(result.getReconciledGrandTotal() - expected) < 0.02);
        result.setBalanceMessage(result.isBalanced()
                ? "分项合计与原始总额勾稽一致"
                : String.format("勾稽差异 %.2f 元（期望 %.2f，实际 %.2f）",
                expected - result.getReconciledGrandTotal(), expected, result.getReconciledGrandTotal()));

        return result;
    }

    private void applySupplyRoomBorrow(
            Map<String, DepartmentSheetSummary> summaryByDept,
            AllocationConfig config,
            AllocationResult result) {
        if (config.getSupplyRoomBorrowCounts() == null || config.getSupplyRoomBorrowCounts().isEmpty()) {
            return;
        }
        DepartmentSheetSummary supplyRoom = summaryByDept.computeIfAbsent("供应室",
                k -> newDepartmentSummary("供应室", "supply_room"));
        for (Map.Entry<String, Integer> entry : config.getSupplyRoomBorrowCounts().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            DepartmentSheetSummary dept = summaryByDept.computeIfAbsent(entry.getKey(),
                    k -> newDepartmentSummary(entry.getKey(), "borrow"));
            double sharePerPack = supplyRoom.getGrossAmount() > 0 && supplyRoom.getPackCount() > 0
                    ? supplyRoom.getGrossAmount() / supplyRoom.getPackCount()
                    : 0;
            double borrowAmount = round2(sharePerPack * entry.getValue());
            dept.setAdjustmentAmount(round2(dept.getAdjustmentAmount() + borrowAmount));
            dept.setNetAmount(round2(dept.getGrossAmount() + dept.getAdjustmentAmount()));
            dept.setLineCount(dept.getLineCount() + 1);

            AllocatedLineItem line = new AllocatedLineItem();
            line.setTargetSheetName(entry.getKey());
            line.setAllocationType("supply_borrow");
            line.setPackCount(entry.getValue());
            line.setAmount(borrowAmount);
            line.setMatchReason("供应室借调分摊");
            result.getAllocatedLines().add(line);
        }
    }

    @Override
    public List<AllocationResult.RosterMatchHint> buildRosterHints(
            Long customerId,
            List<HospitalReconciliationRow> rows,
            List<RosterEntry> roster) {
        List<AllocationResult.RosterMatchHint> hints = new ArrayList<>();
        for (HospitalReconciliationRow row : rows) {
            Optional<RosterEntry> match = matchRosterInText(buildSearchText(row), roster);
            if (match.isEmpty()) {
                continue;
            }
            AllocationResult.RosterMatchHint hint = new AllocationResult.RosterMatchHint();
            hint.setRowId(row.getId());
            hint.setRowNumber(row.getRowNumber());
            hint.setPackName(row.getPackName());
            hint.setMatchedDoctor(match.get().getDoctorName());
            hint.setSuggestedDepartment(match.get().getDepartment());
            hints.add(hint);
        }
        return hints;
    }

    @Override
    public Optional<RosterEntry> matchRosterInText(String text, List<RosterEntry> roster) {
        if (text == null || text.isBlank() || roster == null) {
            return Optional.empty();
        }
        return roster.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .filter(e -> e.getDoctorName() != null && !e.getDoctorName().isBlank())
                .filter(e -> text.contains(e.getDoctorName().trim()))
                .max(Comparator.comparingInt(e -> e.getDoctorName().length()));
    }

    @Override
    public boolean matchesAdjustmentKeyword(String text, AllocationConfig config) {
        if (text == null) {
            return false;
        }
        for (String keyword : config.effectiveAdjustmentKeywords()) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLowTemperatureRow(HospitalReconciliationRow row, AllocationConfig config) {
        String text = buildSearchText(row);
        for (String keyword : config.effectiveLowTempKeywords()) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        String type = row.getType();
        return type != null && type.toUpperCase(Locale.ROOT).contains("低温");
    }

    @Override
    public double rowAmount(HospitalReconciliationRow row) {
        if (row.getCorrectedTotalPrice() != null) {
            return row.getCorrectedTotalPrice();
        }
        if (row.getTotalPrice() != null) {
            return row.getTotalPrice();
        }
        return 0;
    }

    private boolean isOperatingRoomSheet(String sheetName, AllocationConfig config) {
        if (sheetName == null) {
            return false;
        }
        for (String pattern : config.effectiveOrPatterns()) {
            if (sheetName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String resolveDepartmentPrefix(String text, AllocationConfig config) {
        if (config.getDepartmentPrefixRules() == null || text == null) {
            return null;
        }
        for (Map.Entry<String, String> rule : config.getDepartmentPrefixRules().entrySet()) {
            if (text.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return null;
    }

    private String buildSearchText(HospitalReconciliationRow row) {
        StringBuilder sb = new StringBuilder();
        if (row.getPackName() != null) {
            sb.append(row.getPackName());
        }
        if (row.getNotesJson() != null) {
            sb.append(' ').append(row.getNotesJson());
        }
        if (row.getType() != null) {
            sb.append(' ').append(row.getType());
        }
        return sb.toString();
    }

    private AllocatedLineItem toAdjustmentLine(HospitalReconciliationRow row, double amount) {
        AllocatedLineItem item = new AllocatedLineItem();
        item.setSourceRowId(row.getId());
        item.setSourceRowNumber(row.getRowNumber());
        item.setSourceSheetName(row.getSheetName());
        item.setTargetSheetName("费用调整");
        item.setAllocationType("fee_adjustment");
        item.setPackName(row.getPackName());
        item.setCategoryNo(row.getCategoryNo());
        item.setPackCount(row.getPackCount());
        item.setInstrumentCount(row.getInstrumentCount());
        item.setAmount(amount);
        item.setMatchReason("费用调整关键词");
        return item;
    }

    private AllocatedLineItem toAllocatedLine(
            HospitalReconciliationRow row, String targetSheet, String type, double amount) {
        AllocatedLineItem item = new AllocatedLineItem();
        item.setSourceRowId(row.getId());
        item.setSourceRowNumber(row.getRowNumber());
        item.setSourceSheetName(row.getSheetName());
        item.setTargetSheetName(targetSheet);
        item.setAllocationType(type);
        item.setPackName(row.getPackName());
        item.setCategoryNo(row.getCategoryNo());
        item.setPackCount(row.getPackCount());
        item.setInstrumentCount(row.getInstrumentCount());
        item.setAmount(amount);
        return item;
    }

    private void accumulateSummary(
            Map<String, DepartmentSheetSummary> map,
            String deptName,
            String sheetType,
            HospitalReconciliationRow row,
            double gross,
            double adjustment) {
        DepartmentSheetSummary summary = map.computeIfAbsent(deptName,
                k -> newDepartmentSummary(deptName, sheetType));
        summary.setPackCount(summary.getPackCount() + safeInt(row.getPackCount()));
        summary.setInstrumentCount(summary.getInstrumentCount() + safeInt(row.getInstrumentCount()));
        summary.setGrossAmount(round2(summary.getGrossAmount() + gross));
        summary.setAdjustmentAmount(round2(summary.getAdjustmentAmount() + adjustment));
        summary.setNetAmount(round2(summary.getGrossAmount() - summary.getAdjustmentAmount()));
        summary.setLineCount(summary.getLineCount() + 1);
    }

    private DepartmentSheetSummary newDepartmentSummary(String name, String type) {
        DepartmentSheetSummary s = new DepartmentSheetSummary();
        s.setDepartmentName(name);
        s.setSheetType(type);
        return s;
    }

    private BigDecimal externalAmount(ExternalInstrument instrument) {
        if (instrument.getTotalAmount() != null) {
            return instrument.getTotalAmount();
        }
        int packs = instrument.getPackCount() != null ? instrument.getPackCount() : 1;
        BigDecimal unit = instrument.getUnitPrice() != null ? instrument.getUnitPrice() : BigDecimal.ZERO;
        return unit.multiply(BigDecimal.valueOf(packs)).setScale(2, RoundingMode.HALF_UP);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
