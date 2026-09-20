package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.entity.HospitalReconciliationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 计价规则编译 + 内勤结款策略合并（物流/洗涤/加急等）。
 */
@Component
@RequiredArgsConstructor
public class ClerkCompiledRulesResolver {

    private final PricingRuleCompiler pricingRuleCompiler;
    private final ClerkRuleExportService clerkRuleExportService;

    public JsonNode resolve(HospitalReconciliationJob job, JsonNode baseRules) {
        if (job == null || job.getHospitalName() == null || job.getHospitalName().isBlank()) {
            return baseRules;
        }
        JsonNode compiled = pricingRuleCompiler.compile(baseRules, job.getHospitalName());
        return clerkRuleExportService.mergeCompiledForSettlement(job.getHospitalName(), compiled);
    }

    public JsonNode resolve(String hospitalName, JsonNode baseRules) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return baseRules;
        }
        JsonNode compiled = pricingRuleCompiler.compile(baseRules, hospitalName);
        return clerkRuleExportService.mergeCompiledForSettlement(hospitalName, compiled);
    }
}
