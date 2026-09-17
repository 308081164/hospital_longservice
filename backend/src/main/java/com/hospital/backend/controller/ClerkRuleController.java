package com.hospital.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.Result;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.service.ClerkRuleCompiler;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clerk-rules")
@RequiredArgsConstructor
public class ClerkRuleController {

    private final ClerkRuleIndex clerkRuleIndex;
    private final ClerkRuleCompiler clerkRuleCompiler;

    @GetMapping("/index")
    public Result<Map<String, Object>> index() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baselineHash", clerkRuleIndex.baselineHash());
        data.put("customerCount", clerkRuleIndex.customerCodes().size());
        data.put("customerCodes", clerkRuleIndex.customerCodes());
        return Result.success(data);
    }

    @GetMapping("/customers")
    public Result<List<Map<String, Object>>> listCustomers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode baseline : clerkRuleIndex.listBaselines()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerCode", baseline.path("customerCode").asText());
            item.put("customerName", baseline.path("customerName").asText(""));
            item.put("notes", baseline.path("notes").asText(""));
            item.put("ruleCount", baseline.path("rules").size());
            int activeCount = 0;
            int pendingCount = 0;
            if (baseline.path("rules").isArray()) {
                for (JsonNode rule : baseline.path("rules")) {
                    if (rule.path("isActive").asBoolean(true)) {
                        activeCount++;
                    }
                    if ("pending".equalsIgnoreCase(rule.path("migrationStatus").asText())) {
                        pendingCount++;
                    }
                }
            }
            item.put("activeRuleCount", activeCount);
            item.put("pendingMigrationCount", pendingCount);
            list.add(item);
        }
        return Result.success(list);
    }

    @GetMapping("/customers/{customerCode}")
    public Result<Map<String, Object>> getCustomer(@PathVariable String customerCode) {
        JsonNode baseline = clerkRuleIndex.baselineForCustomer(customerCode);
        if (baseline == null) {
            return Result.fail(404, "内勤规则 baseline 不存在: " + customerCode);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseline", baseline);
        data.put("compiled", clerkRuleCompiler.compileForCustomer(customerCode));
        return Result.success(data);
    }

    @GetMapping("/customers/{customerCode}/compiled")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<JsonNode> getCompiled(@PathVariable String customerCode) {
        JsonNode compiled = clerkRuleCompiler.compileForCustomer(customerCode);
        if (compiled == null) {
            return Result.fail(404, "无可编译的内勤规则: " + customerCode);
        }
        return Result.success(compiled);
    }
}
