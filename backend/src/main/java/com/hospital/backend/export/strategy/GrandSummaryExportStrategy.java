package com.hospital.backend.export.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerGroup;
import com.hospital.backend.entity.CustomerGroupMember;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.export.ExportContext;
import com.hospital.backend.export.ExportResult;
import com.hospital.backend.export.SummarySheetWriter;
import com.hospital.backend.mapper.CustomerGroupMapper;
import com.hospital.backend.mapper.CustomerGroupMemberMapper;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GrandSummaryExportStrategy implements ExportStrategy {

    private final SummarySheetWriter summarySheetWriter;
    private final CustomerGroupMemberMapper customerGroupMemberMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final CustomerMapper customerMapper;
    private final HospitalReconciliationJobMapper jobMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public String strategyKey() {
        return ExportTemplateResolverKeys.GRAND_SUMMARY;
    }

    @Override
    public ExportResult export(ExportContext context) throws Exception {
        Map<String, Double> categories = buildMergedCategories(context);
        String sheetName = "总汇总";
        String title = context.getHospitalName() + " — 总汇总";
        byte[] content = summarySheetWriter.buildSingleSheetWorkbook(sheetName, (sheet, headerStyle) ->
                summarySheetWriter.writeGrandSummarySheet(sheet, headerStyle, categories, title));
        String fileName = safeName(context.getHospitalName()) + "_grand_summary_v2_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ExportResult.builder()
                .content(content)
                .fileName(fileName)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .strategyKey(strategyKey())
                .templateId(context.getTemplate().getTemplateId())
                .build();
    }

    private Map<String, Double> buildMergedCategories(ExportContext context) {
        Map<String, Double> merged = new LinkedHashMap<>();
        mergeAllocation(merged, parseAllocationResult(context.getJob().getAllocationResult()));
        if (context.getCustomerId() != null) {
            for (HospitalReconciliationJob sibling : findMergeGroupJobs(context)) {
                if (sibling.getId().equals(context.getJobId())) {
                    continue;
                }
                mergeAllocation(merged, parseAllocationResult(sibling.getAllocationResult()));
            }
        }
        if (merged.isEmpty()) {
            merged.putAll(buildFallbackFromRows(context));
        }
        return merged;
    }

    private void mergeAllocation(Map<String, Double> target, AllocationResult allocation) {
        if (allocation == null) {
            return;
        }
        if (allocation.getPriceSummaryByCategory() != null && !allocation.getPriceSummaryByCategory().isEmpty()) {
            for (Map.Entry<String, Double> entry : allocation.getPriceSummaryByCategory().entrySet()) {
                if ("合计".equals(entry.getKey())) {
                    continue;
                }
                target.merge(entry.getKey(), entry.getValue() != null ? entry.getValue() : 0.0, Double::sum);
            }
        }
        if (allocation.getExternalInstrumentTotal() > 0) {
            target.merge("外来器械", allocation.getExternalInstrumentTotal(), Double::sum);
        }
        if (allocation.getLogisticsTotal() > 0) {
            target.merge("物流费", allocation.getLogisticsTotal(), Double::sum);
        }
        if (allocation.getReconciledGrandTotal() > 0 && target.isEmpty()) {
            target.put("账单合计", allocation.getReconciledGrandTotal());
        }
    }

    private Map<String, Double> buildFallbackFromRows(ExportContext context) {
        Map<String, Double> categories = new LinkedHashMap<>();
        double total = 0.0;
        for (var row : context.getRows()) {
            if ("skipped".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
            total += price != null ? price : 0.0;
        }
        Double logistics = context.getJob().getLogisticsFee();
        if (logistics != null && logistics > 0) {
            categories.put("物流费", logistics);
            total += logistics;
        }
        categories.put("灭菌费", total - (logistics != null ? logistics : 0.0));
        categories.put("合计", total);
        return categories;
    }

    private List<HospitalReconciliationJob> findMergeGroupJobs(ExportContext context) {
        List<CustomerGroupMember> memberships = customerGroupMemberMapper.selectByCustomerId(context.getCustomerId());
        for (CustomerGroupMember membership : memberships) {
            CustomerGroup group = customerGroupMapper.selectById(membership.getGroupId());
            if (group == null || !"settlement_merge".equalsIgnoreCase(group.getGroupType())) {
                continue;
            }
            List<CustomerGroupMember> members = customerGroupMemberMapper.selectByGroupId(group.getId());
            String dateRange = context.getJob().getSourceDateRange();
            List<HospitalReconciliationJob> jobs = new java.util.ArrayList<>();
            jobs.add(context.getJob());
            for (CustomerGroupMember member : members) {
                if (member.getCustomerId().equals(context.getCustomerId())) {
                    continue;
                }
                Customer customer = customerMapper.selectById(member.getCustomerId());
                if (customer == null || customer.getCanonicalName() == null) {
                    continue;
                }
                List<HospitalReconciliationJob> byHospital =
                        jobMapper.selectByHospitalNameOrderByCreatedAtDesc(customer.getCanonicalName());
                for (HospitalReconciliationJob job : byHospital) {
                    if (dateRange != null && dateRange.equals(job.getSourceDateRange())) {
                        jobs.add(job);
                        break;
                    }
                }
            }
            return jobs;
        }
        return List.of(context.getJob());
    }

    private AllocationResult parseAllocationResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AllocationResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "hospital";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
