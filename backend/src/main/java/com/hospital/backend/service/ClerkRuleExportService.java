package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.export.ExportFixedPriceApplier;
import com.hospital.backend.export.ExportStageDiscountApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 内勤规则导出服务：在账单导出阶段应用内勤 baseline，与客服计价规则解耦。
 * <p>
 * 当某院存在已激活的内勤 bill_export 规则时，优先使用内勤 baseline 编译结果，
 * 不再叠加 legacy billingPolicies 中的 export_only 策略（避免双重折扣）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClerkRuleExportService {

    private final ClerkRuleCompiler clerkRuleCompiler;
    private final ExportStageDiscountApplier exportStageDiscountApplier;
    private final ExportFixedPriceApplier exportFixedPriceApplier;
    private final CustomerResolver customerResolver;

    public List<BillRowItem> applyBillExportRules(
            String hospitalName,
            JsonNode legacyCompiled,
            List<BillRowItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        String customerCode = resolveCustomerCode(hospitalName);
        if (customerCode == null) {
            return applyLegacyBillExport(legacyCompiled, rows);
        }
        ObjectNode clerkCompiled = clerkRuleCompiler.compileForCustomer(customerCode);
        if (clerkRuleCompiler.hasActiveBillExportRules(customerCode) && clerkCompiled != null) {
            log.debug("Applying clerk bill_export rules for {}", customerCode);
            rows = exportFixedPriceApplier.apply(clerkCompiled, rows);
            return exportStageDiscountApplier.apply(clerkCompiled, rows);
        }
        return applyLegacyBillExport(legacyCompiled, rows);
    }

    public JsonNode compileSettlementPolicies(String hospitalName, JsonNode legacyCompiled) {
        String customerCode = resolveCustomerCode(hospitalName);
        if (customerCode == null) {
            return legacyCompiled;
        }
        ObjectNode clerkCompiled = clerkRuleCompiler.compileForCustomer(customerCode);
        if (clerkCompiled == null || !clerkCompiled.has("billingPolicies")) {
            return legacyCompiled;
        }
        JsonNode clerkPolicies = clerkCompiled.path("billingPolicies");
        boolean hasSettlement = false;
        if (clerkPolicies.isArray()) {
            for (JsonNode policy : clerkPolicies) {
                if (BillingPolicyApplier.stageMatches(policy, BillingPolicyApplier.STAGE_SETTLEMENT_ONLY)) {
                    hasSettlement = true;
                    break;
                }
            }
        }
        if (!hasSettlement) {
            return legacyCompiled;
        }
        return clerkCompiled;
    }

    private List<BillRowItem> applyLegacyBillExport(JsonNode legacyCompiled, List<BillRowItem> rows) {
        if (legacyCompiled == null) {
            return rows;
        }
        rows = exportFixedPriceApplier.apply(legacyCompiled, rows);
        return exportStageDiscountApplier.apply(legacyCompiled, rows);
    }

    private String resolveCustomerCode(String hospitalName) {
        if (hospitalName == null || hospitalName.isBlank()) {
            return null;
        }
        Optional<com.hospital.backend.entity.Customer> customer = customerResolver.resolveByName(hospitalName);
        return customer.map(com.hospital.backend.entity.Customer::getCode).orElse(null);
    }
}
