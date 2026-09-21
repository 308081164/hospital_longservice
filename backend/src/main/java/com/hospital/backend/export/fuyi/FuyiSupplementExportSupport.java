package com.hospital.backend.export.fuyi;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.service.BillingMonthResolver;
import com.hospital.backend.service.BillingPolicyInspector;
import com.hospital.backend.service.ClerkCompiledRulesResolver;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.LogisticsAllocationService;
import com.hospital.backend.service.LogisticsPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中医附一（ZYY-D1）分科室汇总 / 物流分摊附表数据准备。
 */
@Component
@RequiredArgsConstructor
public class FuyiSupplementExportSupport {

    public static final String CUSTOMER_CODE = "ZYY-D1";

    private final CustomerResolver customerResolver;
    private final ClerkCompiledRulesResolver clerkCompiledRulesResolver;
    private final LogisticsPipelineService logisticsPipelineService;
    private final HospitalPricingRuleMapper pricingRuleMapper;

    public boolean isFuyiHospital(String hospitalName) {
        return customerResolver.resolveByName(hospitalName)
                .map(c -> CUSTOMER_CODE.equals(c.getCode()))
                .orElse(false);
    }

    public Double resolveWashFee(HospitalReconciliationJob job) {
        if (job == null) {
            return null;
        }
        JsonNode baseRules = loadRulesForJob(job);
        JsonNode compiled = clerkCompiledRulesResolver.resolve(job, baseRules);
        String billingMonth = BillingMonthResolver.resolve(job);
        BillingPolicyInspector.OptionalSettlementExtra extra =
                BillingPolicyInspector.resolveSettlementExtra(compiled, billingMonth);
        if (extra == null || extra.amount() <= 0.005) {
            return null;
        }
        return roundCurrency(extra.amount());
    }

    public Double resolveLogisticsFee(HospitalReconciliationJob job) {
        if (job == null || job.getLogisticsFee() == null) {
            return null;
        }
        return roundCurrency(job.getLogisticsFee());
    }

    public Map<String, Double> aggregateDeptTotals(List<HospitalReconciliationRow> rows) {
        Map<String, Double> deptSums = new LinkedHashMap<>();
        if (rows == null) {
            return deptSums;
        }
        for (HospitalReconciliationRow row : rows) {
            String sheet = row.getSheetName() != null && !row.getSheetName().isBlank()
                    ? row.getSheetName().trim()
                    : "(默认)";
            double price = resolveExportRowTotal(row);
            deptSums.merge(sheet, price, Double::sum);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(deptSums.entrySet());
        sorted.sort(Map.Entry.comparingByKey(Collator.getInstance(Locale.CHINA)));
        Map<String, Double> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : sorted) {
            ordered.put(entry.getKey(), roundCurrency(entry.getValue()));
        }
        return ordered;
    }

    public LogisticsAllocationService.AllocationResult resolveLogisticsAllocation(
            HospitalReconciliationJob job,
            List<HospitalReconciliationRow> rows) {
        if (job == null) {
            return new LogisticsAllocationService.AllocationResult(0, 0, List.of());
        }
        JsonNode baseRules = loadRulesForJob(job);
        JsonNode compiled = clerkCompiledRulesResolver.resolve(job, baseRules);
        List<Map<String, Object>> rowMaps = toRowMaps(rows);
        Long customerId = customerResolver.resolveByName(job.getHospitalName())
                .map(c -> c.getId())
                .orElse(null);
        String billingMonth = BillingMonthResolver.resolve(job);
        Map<String, Object> breakdown = logisticsPipelineService.buildBreakdownForJob(
                customerId, job.getId(), billingMonth, compiled, rowMaps, false);
        double totalFee = job.getLogisticsFee() != null
                ? job.getLogisticsFee()
                : breakdown.get("total") instanceof Number n ? n.doubleValue() : 0;
        return logisticsPipelineService.previewDeptAllocation(compiled, rowMaps, totalFee);
    }

    public static double resolveExportRowTotal(HospitalReconciliationRow row) {
        return FuyiSupplementWorkbookBuilder.resolveExportRowTotal(row);
    }

    private JsonNode loadRulesForJob(HospitalReconciliationJob job) {
        if (job.getRuleId() == null) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        HospitalPricingRule rule = pricingRuleMapper.selectById(job.getRuleId());
        if (rule == null || rule.getRulesJson() == null || rule.getRulesJson().isBlank()) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        try {
            return JsonUtils.getObjectMapper().readTree(rule.getRulesJson());
        } catch (Exception e) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
    }

    private static List<Map<String, Object>> toRowMaps(List<HospitalReconciliationRow> rows) {
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        if (rows == null) {
            return rowMaps;
        }
        for (HospitalReconciliationRow row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sheetName", row.getSheetName());
            double exportTotal = resolveExportRowTotal(row);
            map.put("correctedTotalPrice", exportTotal);
            map.put("totalPrice", exportTotal);
            map.put("packCount", row.getPackCount());
            map.put("isUrgent", row.getIsUrgent());
            rowMaps.add(map);
        }
        return rowMaps;
    }

    private static double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
