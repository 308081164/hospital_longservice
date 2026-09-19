package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.export.ExportFixedPriceApplier;
import com.hospital.backend.export.ExportStageDiscountApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 内勤规则导出服务：账单导出阶段应用 clerk baseline，与客服计价规则解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClerkRuleExportService {

    private final ClerkRuleCompiler clerkRuleCompiler;
    private final ExportStageDiscountApplier exportStageDiscountApplier;
    private final ExportFixedPriceApplier exportFixedPriceApplier;
    private final ClerkBillExportApplier clerkBillExportApplier;
    private final ClerkBillPriceRuleApplier clerkBillPriceRuleApplier;
    private final ClerkExportLayoutApplier clerkExportLayoutApplier;
    private final ClerkMonthlySupplementReportGenerator monthlySupplementReportGenerator;
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
            ClerkBillPriceRuleApplier.ApplyResult priced =
                    clerkBillPriceRuleApplier.apply(clerkCompiled, rows);
            rows = priced.rows();
            if (!priced.validationWarnings().isEmpty()) {
                log.debug("Clerk price validation warnings for {}: {}", customerCode, priced.validationWarnings());
            }
            rows = exportFixedPriceApplier.apply(clerkCompiled, rows);
            rows = exportStageDiscountApplier.apply(clerkCompiled, rows);
            rows = clerkBillExportApplier.apply(clerkCompiled, rows);
            return rows;
        }
        return applyLegacyBillExport(legacyCompiled, rows);
    }

    public void applyBillExportLayout(HospitalBillTemplateExportRequest request) {
        if (request == null || request.getHospitalName() == null) {
            return;
        }
        String customerCode = resolveCustomerCode(request.getHospitalName());
        if (customerCode == null) {
            return;
        }
        ObjectNode clerkCompiled = clerkRuleCompiler.compileForCustomer(customerCode);
        clerkExportLayoutApplier.apply(request, clerkCompiled);
    }

    public JsonNode mergeCompiledForSettlement(String hospitalName, JsonNode legacyCompiled) {
        String customerCode = resolveCustomerCode(hospitalName);
        if (customerCode == null) {
            return legacyCompiled;
        }
        ObjectNode clerkCompiled = clerkRuleCompiler.compileForCustomer(customerCode);
        if (clerkCompiled == null) {
            return legacyCompiled;
        }
        if (clerkCompiled.has("billingPolicies") || clerkCompiled.has("clerkRules")) {
            return clerkCompiled;
        }
        return legacyCompiled;
    }

    public JsonNode compileSettlementPolicies(String hospitalName, JsonNode legacyCompiled) {
        return mergeCompiledForSettlement(hospitalName, legacyCompiled);
    }

    public byte[] appendMonthlySupplementSheets(String hospitalName, byte[] billWorkbook) {
        String customerCode = resolveCustomerCode(hospitalName);
        if (customerCode == null || billWorkbook == null) {
            return billWorkbook;
        }
        ObjectNode clerkCompiled = clerkRuleCompiler.compileForCustomer(customerCode);
        if (!monthlySupplementReportGenerator.hasSupplementReports(clerkCompiled)) {
            return billWorkbook;
        }
        return monthlySupplementReportGenerator.appendSupplementSheets(billWorkbook, clerkCompiled);
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
