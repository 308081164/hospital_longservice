package com.hospital.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按科室（sheetName）消毒费比例分摊月物流总费。FR-M6-02 / FR-M6-05。
 */
public final class LogisticsAllocationService {

    private LogisticsAllocationService() {
    }

    public record DeptAmount(String department, double sterilizeTotal) {
    }

    public record DeptAllocation(
            String department,
            double sterilizeTotal,
            double ratio,
            double allocatedFee
    ) {
    }

    public record AllocationResult(
            double totalLogisticsFee,
            double allocatedSum,
            List<DeptAllocation> departments
    ) {
    }

    /**
     * @param totalLogisticsFee 月物流总费
     * @param rows              行数据，需含 sheetName 与 correctedTotalPrice/totalPrice
     */
    public static AllocationResult allocateByDeptRatio(
            double totalLogisticsFee,
            List<Map<String, Object>> rows,
            List<String> excludeDepartments) {
        Map<String, Double> deptTotals = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String dept = resolveDepartment(row);
            if (isExcluded(dept, excludeDepartments)) {
                continue;
            }
            double amount = resolveSterilizeAmount(row);
            if (amount <= 0) {
                continue;
            }
            deptTotals.merge(dept, amount, Double::sum);
        }
        return allocateByDeptRatio(totalLogisticsFee, toDeptAmounts(deptTotals));
    }

    public static AllocationResult allocateByDeptRatio(
            double totalLogisticsFee,
            List<DeptAmount> deptAmounts) {
        double baseTotal = deptAmounts.stream().mapToDouble(DeptAmount::sterilizeTotal).sum();
        List<DeptAllocation> allocations = new ArrayList<>();
        if (baseTotal <= 0 || totalLogisticsFee <= 0 || deptAmounts.isEmpty()) {
            return new AllocationResult(totalLogisticsFee, 0, allocations);
        }

        double allocatedSum = 0;
        for (int i = 0; i < deptAmounts.size(); i++) {
            DeptAmount item = deptAmounts.get(i);
            double ratio = item.sterilizeTotal() / baseTotal;
            double fee;
            if (i == deptAmounts.size() - 1) {
                fee = roundCurrency(totalLogisticsFee - allocatedSum);
            } else {
                fee = roundCurrency(totalLogisticsFee * ratio);
                allocatedSum += fee;
            }
            allocations.add(new DeptAllocation(item.department(), item.sterilizeTotal(), ratio, fee));
        }
        double sum = allocations.stream().mapToDouble(DeptAllocation::allocatedFee).sum();
        return new AllocationResult(totalLogisticsFee, roundCurrency(sum), allocations);
    }

    public static List<Map<String, Object>> toBreakdownList(AllocationResult result) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DeptAllocation dept : result.departments()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("department", dept.department());
            item.put("sterilizeTotal", dept.sterilizeTotal());
            item.put("ratio", roundRatio(dept.ratio()));
            item.put("allocatedFee", dept.allocatedFee());
            list.add(item);
        }
        return list;
    }

    private static List<DeptAmount> toDeptAmounts(Map<String, Double> deptTotals) {
        List<DeptAmount> amounts = new ArrayList<>();
        deptTotals.forEach((dept, total) -> amounts.add(new DeptAmount(dept, roundCurrency(total))));
        return amounts;
    }

    static String resolveDepartment(Map<String, Object> row) {
        Object sheetName = row.get("sheetName");
        if (sheetName == null) {
            sheetName = row.get("sheet_name");
        }
        if (sheetName == null || sheetName.toString().isBlank()) {
            return "(默认)";
        }
        return sheetName.toString().trim();
    }

    static double resolveSterilizeAmount(Map<String, Object> row) {
        Object corrected = row.get("correctedTotalPrice");
        if (corrected == null) {
            corrected = row.get("corrected_total_price");
        }
        if (corrected instanceof Number number) {
            return number.doubleValue();
        }
        Object total = row.get("totalPrice");
        if (total == null) {
            total = row.get("total_price");
        }
        if (total instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private static boolean isExcluded(String department, List<String> excludeDepartments) {
        if (excludeDepartments == null || excludeDepartments.isEmpty()) {
            return false;
        }
        for (String excluded : excludeDepartments) {
            if (excluded != null && excluded.equalsIgnoreCase(department)) {
                return true;
            }
        }
        return false;
    }

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double roundRatio(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
