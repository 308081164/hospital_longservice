package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.dto.request.hospital.BillRowItem;
import com.hospital.backend.dto.request.hospital.HospitalBillTemplateExportRequest;
import com.hospital.backend.export.ExportFixedPriceApplier;
import com.hospital.backend.export.ExportStageDiscountApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 内勤规则导出服务：账单导出阶段应用 clerk baseline，与客服计价规则解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClerkRuleExportService {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

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
                attachValidationNotes(rows, priced.validationWarnings());
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
        if (!clerkCompiled.has("billingPolicies") && !clerkCompiled.has("clerkRules")) {
            return legacyCompiled;
        }
        return mergeCompiledNodes(legacyCompiled, clerkCompiled);
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

    static ObjectNode mergeCompiledNodes(JsonNode legacyCompiled, ObjectNode clerkCompiled) {
        ObjectNode merged = MAPPER.createObjectNode();
        if (legacyCompiled != null && legacyCompiled.isObject()) {
            merged.setAll((ObjectNode) legacyCompiled.deepCopy());
        }

        ArrayNode mergedPolicies = mergeBillingPolicies(
                legacyCompiled != null ? legacyCompiled.path("billingPolicies") : null,
                clerkCompiled.path("billingPolicies"));
        if (!mergedPolicies.isEmpty()) {
            merged.set("billingPolicies", mergedPolicies);
        } else if (merged.has("billingPolicies")) {
            merged.remove("billingPolicies");
        }

        mergeSpecialRules(merged, legacyCompiled, clerkCompiled);

        if (clerkCompiled.has("clerkRules")) {
            merged.set("clerkRules", clerkCompiled.get("clerkRules"));
        }
        if (clerkCompiled.has("exportLayouts")) {
            merged.set("exportLayouts", clerkCompiled.get("exportLayouts"));
        }
        if (clerkCompiled.has("attachmentRefs")) {
            merged.set("attachmentRefs", clerkCompiled.get("attachmentRefs"));
        }
        if (clerkCompiled.has("customerCode")) {
            merged.put("customerCode", clerkCompiled.path("customerCode").asText());
        }
        return merged;
    }

    static ArrayNode mergeBillingPolicies(JsonNode legacyPolicies, JsonNode clerkPolicies) {
        ArrayNode merged = MAPPER.createArrayNode();
        Set<String> clerkKeys = new HashSet<>();
        if (clerkPolicies != null && clerkPolicies.isArray()) {
            for (JsonNode policy : clerkPolicies) {
                merged.add(policy.deepCopy());
                clerkKeys.add(policyMergeKey(policy));
            }
        }
        if (legacyPolicies != null && legacyPolicies.isArray()) {
            for (JsonNode policy : legacyPolicies) {
                if (!clerkKeys.contains(policyMergeKey(policy))) {
                    merged.add(policy.deepCopy());
                }
            }
        }
        return merged;
    }

    static String policyMergeKey(JsonNode policy) {
        String policyType = policy.path("policyType").asText("");
        String applyStage = policy.path("params").path("applyStage").asText("");
        if (applyStage.isBlank()) {
            applyStage = policy.path("applyStage").asText("default");
        }
        return policyType + "|" + applyStage;
    }

    private static void mergeSpecialRules(ObjectNode merged, JsonNode legacyCompiled, ObjectNode clerkCompiled) {
        ObjectNode legacySpecial = legacyCompiled != null && legacyCompiled.has("specialRules")
                ? (ObjectNode) legacyCompiled.path("specialRules").deepCopy()
                : MAPPER.createObjectNode();
        ObjectNode clerkSpecial = clerkCompiled.has("specialRules")
                ? (ObjectNode) clerkCompiled.path("specialRules")
                : MAPPER.createObjectNode();

        ArrayNode fixedPrices = MAPPER.createArrayNode();
        Set<String> clerkFixedNames = new HashSet<>();
        JsonNode clerkFixed = clerkSpecial.path("fixedPrices");
        if (clerkFixed.isArray()) {
            for (JsonNode fixed : clerkFixed) {
                fixedPrices.add(fixed.deepCopy());
                clerkFixedNames.add(fixed.path("name").asText(""));
            }
        }
        JsonNode legacyFixed = legacySpecial.path("fixedPrices");
        if (legacyFixed.isArray()) {
            for (JsonNode fixed : legacyFixed) {
                String name = fixed.path("name").asText("");
                if (!clerkFixedNames.contains(name)) {
                    fixedPrices.add(fixed.deepCopy());
                }
            }
        }
        if (!fixedPrices.isEmpty()) {
            ObjectNode specialRules = merged.has("specialRules")
                    ? (ObjectNode) merged.get("specialRules")
                    : MAPPER.createObjectNode();
            specialRules.set("fixedPrices", fixedPrices);
            Iterator<String> fields = legacySpecial.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!"fixedPrices".equals(field) && !specialRules.has(field)) {
                    specialRules.set(field, legacySpecial.get(field));
                }
            }
            merged.set("specialRules", specialRules);
        }
    }

    private static void attachValidationNotes(List<BillRowItem> rows, List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        BillRowItem target = rows.get(0);
        if (target.getNotes() == null) {
            target.setNotes(new java.util.ArrayList<>(warnings));
        } else {
            target.getNotes().addAll(warnings);
        }
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
