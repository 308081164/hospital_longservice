package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.billing.BillingRuleConflictCheckRequest;
import com.hospital.backend.dto.request.billing.BillingRuleImportConfirmRequest;
import com.hospital.backend.dto.request.billing.BillingRuleImportPreviewRequest;
import com.hospital.backend.dto.request.billing.BillingRuleSimulateRequest;
import com.hospital.backend.dto.response.billing.BillingRuleChangeLogResponse;
import com.hospital.backend.dto.response.billing.BillingRuleSimulateResponse;
import com.hospital.backend.dto.response.billing.RuleVerificationResult;
import com.hospital.backend.service.BaselineRuleSyncService;
import com.hospital.backend.service.BillingRuleGroupSyncService;
import com.hospital.backend.service.BillingRuleImportService;
import com.hospital.backend.service.BillingRuleSimulatorService;
import com.hospital.backend.service.RuleChangeAuditService;
import com.hospital.backend.service.RuleQuarantineService;
import com.hospital.backend.service.RulesVerificationService;
import com.hospital.backend.entity.CustomerProductRuleTombstone;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.entity.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.config.BaselineRuleIndex;
import com.hospital.backend.common.JsonUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    private final RuleQuarantineService ruleQuarantineService;
    private final BaselineRuleSyncService baselineRuleSyncService;
    private final RulesVerificationService rulesVerificationService;
    private final BaselineRuleIndex baselineRuleIndex;
    private final CustomerMapper customerMapper;

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

    @GetMapping("/quarantine")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<List<CustomerProductRuleTombstone>> listQuarantine(
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "100") int limit) {
        if (customerId != null) {
            return Result.success(ruleQuarantineService.listActiveQuarantines(customerId));
        }
        return Result.success(ruleQuarantineService.listAllActiveQuarantines(limit));
    }

    @PostMapping("/quarantine/{tombstoneId}/restore")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> restoreQuarantine(
            @PathVariable Long tombstoneId,
            @RequestParam(required = false, defaultValue = "api-restore") String operatorName) {
        boolean ok = ruleQuarantineService.restoreQuarantine(tombstoneId, operatorName);
        if (!ok) {
            return Result.fail(404, "隔离记录不存在或已恢复");
        }
        return Result.success(Map.of("restored", true, "tombstoneId", tombstoneId));
    }

    @GetMapping("/quarantine/{tombstoneId}/seed-draft")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> quarantineSeedDraft(@PathVariable Long tombstoneId) {
        CustomerProductRuleTombstone tombstone = ruleQuarantineService.getTombstone(tombstoneId);
        if (tombstone == null || tombstone.getRestoredAt() != null) {
            return Result.fail(404, "隔离记录不存在或已恢复");
        }
        return Result.success(ruleQuarantineService.buildSeedPatchDraft(tombstone));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> exportRules(
            @RequestParam(required = false) String customerCode,
            @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            return Result.success(baselineRuleSyncService.exportAll());
        }
        if (customerCode == null || customerCode.isBlank()) {
            return Result.fail(400, "请指定 customerCode 或 all=true");
        }
        Customer customer = customerMapper.selectByCode(customerCode);
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }
        return Result.success(baselineRuleSyncService.exportCustomer(customer.getId()));
    }

    @PostMapping("/import/baseline")
    @PreAuthorize("hasAnyRole('SUPER','R_BILLING_CONFIG')")
    public Result<Map<String, Object>> importBaseline(
            @RequestParam(required = false) String customerCode,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            int imported;
            RuleVerificationResult verification = null;
            if (all) {
                imported = baselineRuleSyncService.importAllBaselines(dryRun);
                if (!dryRun) {
                    rulesVerificationService.verifyAll();
                }
            } else {
                if (customerCode == null || customerCode.isBlank()) {
                    return Result.fail(400, "请指定 customerCode 或 all=true");
                }
                Customer customer = customerMapper.selectByCode(customerCode);
                if (customer == null) {
                    return Result.fail(404, "客户不存在");
                }
                JsonNode baseline = body != null && !body.isEmpty()
                        ? JsonUtils.getObjectMapper().valueToTree(body)
                        : baselineRuleIndex.baselineForCustomer(customerCode);
                if (baseline == null) {
                    return Result.fail(404, "baseline 不存在");
                }
                imported = baselineRuleSyncService.importCustomerBaseline(customer.getId(), baseline, dryRun);
                if (!dryRun) {
                    verification = rulesVerificationService.verifyCustomer(customerCode);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("imported", imported);
            data.put("dryRun", dryRun);
            if (verification != null) {
                data.put("verification", verification);
            }
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @GetMapping("/verify")
    public Result<Object> verifyRules(
            @RequestParam(required = false) String customerCode,
            @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            return Result.success(rulesVerificationService.verifyAll());
        }
        if (customerCode == null || customerCode.isBlank()) {
            return Result.fail(400, "请指定 customerCode 或 all=true");
        }
        return Result.success(rulesVerificationService.verifyCustomer(customerCode));
    }
}
