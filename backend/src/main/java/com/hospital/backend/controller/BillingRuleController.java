package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.billing.BillingRuleConflictCheckRequest;
import com.hospital.backend.dto.request.billing.BillingRuleImportConfirmRequest;
import com.hospital.backend.dto.request.billing.BillingRuleImportPreviewRequest;
import com.hospital.backend.dto.request.billing.BillingRuleSimulateRequest;
import com.hospital.backend.dto.response.billing.BillingRuleChangeLogResponse;
import com.hospital.backend.dto.response.billing.BillingRuleSimulateResponse;
import com.hospital.backend.service.BillingRuleGroupSyncService;
import com.hospital.backend.service.BillingRuleImportService;
import com.hospital.backend.service.BillingRuleSimulatorService;
import com.hospital.backend.service.RuleChangeAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing-rules")
@RequiredArgsConstructor
public class BillingRuleController {

    private final BillingRuleSimulatorService simulatorService;
    private final BillingRuleImportService importService;
    private final BillingRuleGroupSyncService groupSyncService;
    private final RuleChangeAuditService auditService;

    @PostMapping("/simulate")
    public Result<BillingRuleSimulateResponse> simulate(@Valid @RequestBody BillingRuleSimulateRequest request) {
        return simulatorService.simulate(request);
    }

    @PostMapping("/validate-conflicts")
    public Result<Map<String, Object>> validateConflicts(@Valid @RequestBody BillingRuleConflictCheckRequest request) {
        return groupSyncService.detectConflicts(request.getRules());
    }

    @GetMapping("/change-log")
    public Result<List<BillingRuleChangeLogResponse>> listChangeLog(
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(auditService.listRecentChanges(customerId, limit));
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> previewImport(@Valid @RequestBody BillingRuleImportPreviewRequest request) {
        return importService.previewImport(request);
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> confirmImport(@Valid @RequestBody BillingRuleImportConfirmRequest request) {
        return importService.confirmImport(request);
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> listTemplates() {
        return groupSyncService.listBuiltinTemplates();
    }

    @PostMapping("/customers/{targetId}/copy-from/{sourceId}")
    public Result<Map<String, Object>> copyRulesFromCustomer(
            @PathVariable Long targetId,
            @PathVariable Long sourceId,
            @RequestParam(required = false) String operatorName) {
        return groupSyncService.copyRulesFromCustomer(targetId, sourceId, operatorName);
    }

    @PostMapping("/customers/{customerId}/sync-rule-group")
    public Result<Map<String, Object>> syncRuleGroup(
            @PathVariable Long customerId,
            @RequestParam(required = false) String operatorName) {
        groupSyncService.syncDefaultGroupFromProductRules(customerId, operatorName);
        return Result.success(Map.of("synced", true));
    }
}
