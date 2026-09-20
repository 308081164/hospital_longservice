package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.HospitalSettlementTemplateExportRequest;
import com.hospital.backend.dto.request.hospital.SettlementFeeRow;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.export.SettlementJobEnricher;
import com.hospital.backend.export.SettlementPeriodFormatter;
import com.hospital.backend.export.SettlementTemplateFiller;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结款函导出前按 Job + 内勤规则填充费用行（灭菌/物流/洗涤/加急等）。
 */
@Component
@RequiredArgsConstructor
public class ClerkSettlementRequestEnricher {

    private final HospitalReconciliationJobMapper jobMapper;
    private final HospitalReconciliationRowMapper rowMapper;
    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final ClerkCompiledRulesResolver clerkCompiledRulesResolver;
    private final SettlementJobEnricher settlementJobEnricher;
    private final SettlementTemplateFiller settlementTemplateFiller;

    public void enrich(HospitalSettlementTemplateExportRequest request) {
        if (request == null || request.getTemplateId() == null || request.getTemplateId().isBlank()) {
            return;
        }
        try {
            Long jobId = Long.parseLong(request.getTemplateId());
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                return;
            }
            List<HospitalReconciliationRow> rows =
                    rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
            JsonNode baseRules = loadRulesForJob(job);
            JsonNode compiled = clerkCompiledRulesResolver.resolve(job, baseRules);
            settlementJobEnricher.enrichForExport(job, compiled, rows);

            if (request.getHospitalDisplayName() == null || request.getHospitalDisplayName().isBlank()) {
                request.setHospitalDisplayName(job.getHospitalName());
            }
            if (request.getHospitalName() == null || request.getHospitalName().isBlank()) {
                request.setHospitalName(job.getHospitalName());
            }
            SettlementPeriodFormatter.parse(job.getSourceDateRange()).ifPresent(period -> {
                if (request.getDateRangeText() == null || request.getDateRangeText().isBlank()) {
                    request.setDateRangeText(SettlementPeriodFormatter.formatSettlementIntro(period));
                }
                if (request.getClosingText() == null || request.getClosingText().isBlank()) {
                    request.setClosingText(SettlementPeriodFormatter.buildClosingText(null, period));
                }
            });

            double sterilizeTotal = job.getCorrectedTotalPrice() != null
                    ? job.getCorrectedTotalPrice()
                    : rows.stream()
                            .mapToDouble(r -> r.getCorrectedTotalPrice() != null
                                    ? r.getCorrectedTotalPrice()
                                    : (r.getTotalPrice() != null ? r.getTotalPrice() : 0))
                            .sum();
            var fillerRows = settlementTemplateFiller.buildFeeRows(job, sterilizeTotal, compiled, rows);
            request.setFeeRows(fillerRows.stream().map(this::toDto).toList());
            request.setTotalAmount(settlementTemplateFiller.computeTotalAmount(fillerRows));
        } catch (NumberFormatException ignored) {
            // templateId 非 jobId 时跳过
        }
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

    private SettlementFeeRow toDto(SettlementTemplateFiller.SettlementFeeRow row) {
        SettlementFeeRow dto = new SettlementFeeRow();
        dto.setIndexLabel(String.valueOf(row.getSequence()));
        dto.setItemLabel(row.getItemName());
        dto.setAmount(row.getAmount());
        dto.setRemark(row.getRemark());
        return dto;
    }
}
