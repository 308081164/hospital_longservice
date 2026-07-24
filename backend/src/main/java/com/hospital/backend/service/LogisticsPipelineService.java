package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.CustomerGroup;
import com.hospital.backend.entity.CustomerGroupMember;
import com.hospital.backend.entity.LogisticsCard;
import com.hospital.backend.entity.LogisticsImport;
import com.hospital.backend.mapper.CustomerGroupMapper;
import com.hospital.backend.mapper.CustomerGroupMemberMapper;
import com.hospital.backend.mapper.LogisticsCardMapper;
import com.hospital.backend.mapper.LogisticsCardTransactionMapper;
import com.hospital.backend.mapper.LogisticsImportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 物流计费流水线：独立导入 → 跨客户合并 → 科室分摊 → 物流卡扣减。
 */
@Service
@RequiredArgsConstructor
public class LogisticsPipelineService {

    private final LogisticsImportMapper logisticsImportMapper;
    private final LogisticsCardMapper logisticsCardMapper;
    private final LogisticsCardTransactionMapper logisticsCardTransactionMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final CustomerGroupMemberMapper customerGroupMemberMapper;

    public Optional<LogisticsFeeCalculator.Result> computeForJob(
            Long customerId,
            Long jobId,
            String billingMonth,
            JsonNode compiledRules,
            List<Map<String, Object>> rows) {
        List<LogisticsImport> imports = resolveImports(customerId, jobId, billingMonth);
        Optional<LogisticsFeeCalculator.Result> base = LogisticsFeeCalculator.compute(compiledRules, rows, imports);
        if (base.isEmpty()) {
            return Optional.empty();
        }

        LogisticsFeeCalculator.LogisticsPolicyParams params =
                LogisticsFeeCalculator.resolvePolicyParams(compiledRules);
        double totalFee = applyCrossCustomerMerge(customerId, compiledRules, rows, imports, params, base.get());
        Map<String, Object> breakdown = new LinkedHashMap<>(
                LogisticsFeeCalculator.toBreakdownMap(base.get(), params.waivedTrips()));
        breakdown.put("total", totalFee);

        if ("dept_ratio".equalsIgnoreCase(params.allocationMode())) {
            LogisticsAllocationService.AllocationResult allocation =
                    LogisticsAllocationService.allocateByDeptRatio(
                            totalFee, rows, params.excludeDepartments());
            breakdown.put("allocationMode", "dept_ratio");
            breakdown.put("deptAllocations", LogisticsAllocationService.toBreakdownList(allocation));
            breakdown.put("allocationSum", allocation.allocatedSum());
        } else if (params.allocationMode() != null
                && !"none".equalsIgnoreCase(params.allocationMode())) {
            breakdown.put("allocationMode", params.allocationMode());
        }

        applyCardDeduction(customerId, jobId, totalFee, breakdown, true, params);
        return Optional.of(new LogisticsFeeCalculator.Result(
                base.get().tripCount(),
                base.get().feePerTrip(),
                ((Number) breakdown.getOrDefault("payableFee", totalFee)).doubleValue(),
                base.get().feeSource(),
                base.get().policyId(),
                base.get().tripSource()));
    }

    public Map<String, Object> buildBreakdownForJob(
            Long customerId,
            Long jobId,
            String billingMonth,
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            boolean applyCardDeduction) {
        List<LogisticsImport> imports = resolveImports(customerId, jobId, billingMonth);
        Optional<LogisticsFeeCalculator.Result> base = LogisticsFeeCalculator.compute(compiledRules, rows, imports);
        if (base.isEmpty()) {
            return Map.of();
        }

        LogisticsFeeCalculator.LogisticsPolicyParams params =
                LogisticsFeeCalculator.resolvePolicyParams(compiledRules);
        double totalFee = applyCrossCustomerMerge(customerId, compiledRules, rows, imports, params, base.get());
        Map<String, Object> breakdown = new LinkedHashMap<>(
                LogisticsFeeCalculator.toBreakdownMap(base.get(), params.waivedTrips()));
        breakdown.put("total", totalFee);

        if ("dept_ratio".equalsIgnoreCase(params.allocationMode())) {
            LogisticsAllocationService.AllocationResult allocation =
                    LogisticsAllocationService.allocateByDeptRatio(
                            totalFee, rows, params.excludeDepartments());
            breakdown.put("allocationMode", "dept_ratio");
            breakdown.put("deptAllocations", LogisticsAllocationService.toBreakdownList(allocation));
            breakdown.put("allocationSum", allocation.allocatedSum());
        } else if (params.allocationMode() != null
                && !"none".equalsIgnoreCase(params.allocationMode())) {
            breakdown.put("allocationMode", params.allocationMode());
        }

        applyCardDeduction(customerId, jobId, totalFee, breakdown, applyCardDeduction, params);
        return breakdown;
    }

    public LogisticsAllocationService.AllocationResult previewDeptAllocation(
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            double totalLogisticsFee) {
        LogisticsFeeCalculator.LogisticsPolicyParams params =
                LogisticsFeeCalculator.resolvePolicyParams(compiledRules);
        return LogisticsAllocationService.allocateByDeptRatio(
                totalLogisticsFee, rows, params.excludeDepartments());
    }

    private List<LogisticsImport> resolveImports(Long customerId, Long jobId, String billingMonth) {
        if (jobId != null) {
            List<LogisticsImport> byJob = logisticsImportMapper.selectByJobId(jobId);
            if (!byJob.isEmpty()) {
                return byJob;
            }
        }
        if (customerId != null && billingMonth != null && !billingMonth.isBlank()) {
            return logisticsImportMapper.selectByCustomerAndMonth(customerId, billingMonth);
        }
        if (customerId != null) {
            return logisticsImportMapper.selectByCustomerId(customerId);
        }
        return List.of();
    }

    private double applyCrossCustomerMerge(
            Long customerId,
            JsonNode compiledRules,
            List<Map<String, Object>> rows,
            List<LogisticsImport> imports,
            LogisticsFeeCalculator.LogisticsPolicyParams params,
            LogisticsFeeCalculator.Result base) {
        Optional<CustomerGroup> mergeGroup = findLogisticsMergeGroup(customerId, params);
        if (mergeGroup.isEmpty() || !shouldApplyCrossHospitalMerge(params)) {
            return base.totalFee();
        }

        List<CustomerGroupMember> members =
                customerGroupMemberMapper.selectByGroupId(mergeGroup.get().getId());
        List<Long> memberIds = members.stream().map(CustomerGroupMember::getCustomerId).toList();
        Map<Long, Double> shareRatios = new LinkedHashMap<>();
        for (CustomerGroupMember member : members) {
            if (member.getShareRatio() != null) {
                shareRatios.put(member.getCustomerId(), member.getShareRatio());
            }
        }

        List<LogisticsMergeService.CustomerDayActivity> activities = new ArrayList<>();
        activities.addAll(toActivities(customerId, rows, imports, params));
        for (CustomerGroupMember member : members) {
            if (customerId.equals(member.getCustomerId())) {
                continue;
            }
            List<LogisticsImport> peerImports = logisticsImportMapper.selectByCustomerId(member.getCustomerId());
            activities.addAll(toActivities(member.getCustomerId(), List.of(), peerImports, params));
        }

        LogisticsMergeService.MergeResult merged = LogisticsMergeService.mergeSameDayCrossCustomer(
                params.feePerTrip(),
                customerId,
                memberIds,
                activities,
                shareRatios,
                resolveCrossHospitalMode(params),
                params.singleOwnerCustomerId());
        return merged.totalFeeForCustomer() > 0 ? merged.totalFeeForCustomer() : base.totalFee();
    }

    private static boolean shouldApplyCrossHospitalMerge(LogisticsFeeCalculator.LogisticsPolicyParams params) {
        if (params == null) {
            return false;
        }
        String mode = params.allocationMode() != null ? params.allocationMode().toLowerCase() : "none";
        if ("dept_ratio".equals(mode) || "none".equals(mode)) {
            return params.mergeSameDay() && params.logisticsMergeGroupId() != null;
        }
        if ("equal".equals(mode)
                || "proportional".equals(mode)
                || "single_owner".equals(mode)
                || "cross_hospital_merge".equals(mode)) {
            return params.mergeSameDay();
        }
        return params.mergeSameDay();
    }

    private static String resolveCrossHospitalMode(LogisticsFeeCalculator.LogisticsPolicyParams params) {
        if (params == null || params.allocationMode() == null) {
            return "cross_hospital_merge";
        }
        String mode = params.allocationMode().toLowerCase();
        if ("equal".equals(mode)
                || "proportional".equals(mode)
                || "single_owner".equals(mode)
                || "cross_hospital_merge".equals(mode)) {
            return mode;
        }
        return "cross_hospital_merge";
    }

    private List<LogisticsMergeService.CustomerDayActivity> toActivities(
            Long customerId,
            List<Map<String, Object>> rows,
            List<LogisticsImport> imports,
            LogisticsFeeCalculator.LogisticsPolicyParams params) {
        List<LogisticsMergeService.CustomerDayActivity> activities = new ArrayList<>();
        if ("import".equalsIgnoreCase(params.tripSource()) && imports != null) {
            for (LogisticsImport item : imports) {
                if (item.getTripDate() != null
                        && LogisticsFeeCalculator.matchesWeekday(item.getTripDate(), params.billingWeekdays())) {
                    activities.add(new LogisticsMergeService.CustomerDayActivity(customerId, item.getTripDate()));
                }
            }
            return activities;
        }
        for (Map<String, Object> row : rows) {
            Object deliveryDate = row.get("deliveryDate");
            if (deliveryDate == null) {
                deliveryDate = row.get("delivery_date");
            }
            if (deliveryDate == null) {
                continue;
            }
            LocalDate parsed = LogisticsFeeCalculator.parseDate(deliveryDate.toString().split("\\s+")[0]);
            if (parsed != null && LogisticsFeeCalculator.matchesWeekday(parsed, params.billingWeekdays())) {
                activities.add(new LogisticsMergeService.CustomerDayActivity(customerId, parsed));
            }
        }
        return activities;
    }

    private Optional<CustomerGroup> findLogisticsMergeGroup(
            Long customerId,
            LogisticsFeeCalculator.LogisticsPolicyParams params) {
        if (customerId == null) {
            return Optional.empty();
        }
        if (params != null && params.logisticsMergeGroupId() != null) {
            CustomerGroup group = customerGroupMapper.selectById(params.logisticsMergeGroupId());
            if (group != null
                    && Boolean.TRUE.equals(group.getIsActive())
                    && "logistics_merge".equalsIgnoreCase(group.getGroupType())) {
                return Optional.of(group);
            }
        }
        List<CustomerGroupMember> memberships = customerGroupMemberMapper.selectByCustomerId(customerId);
        for (CustomerGroupMember membership : memberships) {
            CustomerGroup group = customerGroupMapper.selectById(membership.getGroupId());
            if (group != null
                    && Boolean.TRUE.equals(group.getIsActive())
                    && "logistics_merge".equalsIgnoreCase(group.getGroupType())) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    private void applyCardDeduction(
            Long customerId,
            Long jobId,
            double totalFee,
            Map<String, Object> breakdown,
            boolean persistDeduction,
            LogisticsFeeCalculator.LogisticsPolicyParams params) {
        if (params != null && !params.cardDeductionEnabled()) {
            breakdown.put("payableFee", totalFee);
            breakdown.put("cardDeductionSkipped", true);
            return;
        }
        if (params != null && "none".equalsIgnoreCase(params.cardDeductMode())) {
            breakdown.put("payableFee", totalFee);
            breakdown.put("cardDeductionSkipped", true);
            return;
        }
        LogisticsCard card = logisticsCardMapper.selectActiveByCustomerId(customerId);
        if (card == null || totalFee <= 0) {
            breakdown.put("payableFee", totalFee);
            return;
        }
        double balance = card.getBalance() != null ? card.getBalance() : 0;
        double deduct = Math.min(balance, totalFee);
        if (params != null && params.cardMonthlyCap() != null && params.cardMonthlyCap() > 0) {
            deduct = Math.min(deduct, params.cardMonthlyCap());
        }
        double payable = LogisticsFeeCalculator.roundCurrency(totalFee - deduct);
        breakdown.put("cardId", card.getId());
        breakdown.put("cardBalanceBefore", balance);
        breakdown.put("cardDeducted", deduct);
        breakdown.put("cardBalanceAfter", LogisticsFeeCalculator.roundCurrency(balance - deduct));
        breakdown.put("payableFee", payable);
        if (persistDeduction && deduct > 0 && jobId != null) {
            double newBalance = LogisticsFeeCalculator.roundCurrency(balance - deduct);
            card.setBalance(newBalance);
            logisticsCardMapper.updateById(card);
            com.hospital.backend.entity.LogisticsCardTransaction tx =
                    new com.hospital.backend.entity.LogisticsCardTransaction();
            tx.setCardId(card.getId());
            tx.setTransactionType("DEDUCT");
            tx.setAmount(deduct);
            tx.setBalanceAfter(newBalance);
            tx.setJobId(jobId);
            tx.setRemark("对账任务 #" + jobId + " 物流费扣减");
            logisticsCardTransactionMapper.insert(tx);
        }
    }

    public String toBreakdownJson(Map<String, Object> breakdown) {
        return JsonUtils.toJson(breakdown);
    }
}
