package com.hospital.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨客户/跨院区同日物流合并计费。FR-M6-03 / FR-M6-06。
 */
public final class LogisticsMergeService {

    private LogisticsMergeService() {
    }

    public record CustomerDayActivity(Long customerId, LocalDate tripDate) {
    }

    public record CustomerShare(Long customerId, double shareRatio, double allocatedFee) {
    }

    public record DayMergeDetail(
            LocalDate tripDate,
            int activeCustomerCount,
            double feePerTrip,
            List<CustomerShare> shares
    ) {
    }

    public record MergeResult(
            double totalFeeForCustomer,
            List<DayMergeDetail> dayDetails
    ) {
    }

    /**
     * 同一自然日组内多个客户均有发货/趟次时，按均分或自定义比例拆分单次物流费。
     *
     * @param feePerTrip         单次物流单价
     * @param targetCustomerId   当前账单客户
     * @param groupCustomerIds   合并组内全部客户 ID
     * @param activities         组内各客户的发货日/趟次日
     * @param customShareRatios  客户自定义比例（可为空，空则均分）
     */
    public static MergeResult mergeSameDayCrossCustomer(
            double feePerTrip,
            Long targetCustomerId,
            List<Long> groupCustomerIds,
            List<CustomerDayActivity> activities,
            Map<Long, Double> customShareRatios) {
        return mergeSameDayCrossCustomer(
                feePerTrip,
                targetCustomerId,
                groupCustomerIds,
                activities,
                customShareRatios,
                "cross_hospital_merge",
                null);
    }

    /**
     * @param allocationMode       equal | proportional | single_owner | cross_hospital_merge
     * @param singleOwnerCustomerId required when allocationMode=single_owner
     */
    public static MergeResult mergeSameDayCrossCustomer(
            double feePerTrip,
            Long targetCustomerId,
            List<Long> groupCustomerIds,
            List<CustomerDayActivity> activities,
            Map<Long, Double> customShareRatios,
            String allocationMode,
            Long singleOwnerCustomerId) {
        if (targetCustomerId == null || groupCustomerIds == null || groupCustomerIds.isEmpty()) {
            return new MergeResult(0, List.of());
        }
        Set<Long> groupSet = new LinkedHashSet<>(groupCustomerIds);
        Map<LocalDate, Set<Long>> dateToCustomers = new LinkedHashMap<>();
        for (CustomerDayActivity activity : activities) {
            if (activity.customerId() == null || activity.tripDate() == null) {
                continue;
            }
            if (!groupSet.contains(activity.customerId())) {
                continue;
            }
            dateToCustomers.computeIfAbsent(activity.tripDate(), k -> new LinkedHashSet<>())
                    .add(activity.customerId());
        }

        List<DayMergeDetail> details = new ArrayList<>();
        double customerTotal = 0;
        for (Map.Entry<LocalDate, Set<Long>> entry : dateToCustomers.entrySet()) {
            LocalDate date = entry.getKey();
            List<Long> activeCustomers = new ArrayList<>(entry.getValue());
            if (!activeCustomers.contains(targetCustomerId)) {
                continue;
            }
            List<CustomerShare> shares = splitDayFee(
                    feePerTrip,
                    activeCustomers,
                    customShareRatios,
                    allocationMode,
                    singleOwnerCustomerId);
            double targetFee = shares.stream()
                    .filter(s -> targetCustomerId.equals(s.customerId()))
                    .mapToDouble(CustomerShare::allocatedFee)
                    .findFirst()
                    .orElse(0);
            customerTotal += targetFee;
            details.add(new DayMergeDetail(date, activeCustomers.size(), feePerTrip, shares));
        }
        return new MergeResult(roundCurrency(customerTotal), details);
    }

    static List<CustomerShare> splitDayFee(
            double feePerTrip,
            List<Long> activeCustomers,
            Map<Long, Double> customShareRatios) {
        return splitDayFee(feePerTrip, activeCustomers, customShareRatios, "cross_hospital_merge", null);
    }

    static List<CustomerShare> splitDayFee(
            double feePerTrip,
            List<Long> activeCustomers,
            Map<Long, Double> customShareRatios,
            String allocationMode,
            Long singleOwnerCustomerId) {
        List<CustomerShare> shares = new ArrayList<>();
        if (activeCustomers.isEmpty()) {
            return shares;
        }

        if ("single_owner".equalsIgnoreCase(allocationMode) && singleOwnerCustomerId != null) {
            for (Long customerId : activeCustomers) {
                double fee = singleOwnerCustomerId.equals(customerId) ? roundCurrency(feePerTrip) : 0;
                shares.add(new CustomerShare(
                        customerId,
                        singleOwnerCustomerId.equals(customerId) ? 1.0 : 0.0,
                        fee));
            }
            return shares;
        }

        boolean useCustom = "proportional".equalsIgnoreCase(allocationMode)
                || "equal".equalsIgnoreCase(allocationMode)
                || "cross_hospital_merge".equalsIgnoreCase(allocationMode);
        boolean hasCustom = useCustom
                && customShareRatios != null
                && activeCustomers.stream()
                        .anyMatch(id -> customShareRatios.containsKey(id) && customShareRatios.get(id) != null);
        if ("equal".equalsIgnoreCase(allocationMode)) {
            hasCustom = false;
        }
        if (hasCustom) {
            double ratioSum = 0;
            for (Long customerId : activeCustomers) {
                Double ratio = customShareRatios.get(customerId);
                ratioSum += ratio != null ? ratio : 0;
            }
            if (ratioSum <= 0) {
                hasCustom = false;
            } else {
                double allocated = 0;
                for (int i = 0; i < activeCustomers.size(); i++) {
                    Long customerId = activeCustomers.get(i);
                    double ratio = customShareRatios.getOrDefault(customerId, 0.0) / ratioSum;
                    double fee = i == activeCustomers.size() - 1
                            ? roundCurrency(feePerTrip - allocated)
                            : roundCurrency(feePerTrip * ratio);
                    allocated += fee;
                    shares.add(new CustomerShare(customerId, ratio, fee));
                }
                return shares;
            }
        }
        double equalShare = feePerTrip / activeCustomers.size();
        double allocated = 0;
        for (int i = 0; i < activeCustomers.size(); i++) {
            Long customerId = activeCustomers.get(i);
            double fee = i == activeCustomers.size() - 1
                    ? roundCurrency(feePerTrip - allocated)
                    : roundCurrency(equalShare);
            allocated += fee;
            shares.add(new CustomerShare(customerId, 1.0 / activeCustomers.size(), fee));
        }
        return shares;
    }

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
