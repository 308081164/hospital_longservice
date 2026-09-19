package com.hospital.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.Result;
import com.hospital.backend.config.ClerkRuleIndex;
import com.hospital.backend.service.ClerkRuleCompiler;
import com.hospital.backend.service.ClerkMonthlySupplementReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ClerkMonthlySupplementReportGenerator monthlySupplementReportGenerator;

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

    @GetMapping("/attachments/preview")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<List<Map<String, Object>>> previewAttachment(@RequestParam String path) {
        return Result.success(monthlySupplementReportGenerator.previewAttachment(path));
    }

    @GetMapping("/attachments/file")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public ResponseEntity<Resource> downloadAttachment(@RequestParam String path) {
        String classpath = resolveAttachmentClasspath(path);
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        MediaType mediaType = filename.toLowerCase().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    private static String resolveAttachmentClasspath(String path) {
        if (path == null || path.isBlank()) {
            return "clerk-rules/attachments/";
        }
        if (path.startsWith("clerk-rules/")) {
            return path;
        }
        if (path.startsWith("attachments/")) {
            return "clerk-rules/" + path;
        }
        return "clerk-rules/attachments/" + path;
    }
}
