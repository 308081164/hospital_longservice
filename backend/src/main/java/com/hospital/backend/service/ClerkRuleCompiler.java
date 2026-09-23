package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 将内勤规则 baseline 编译为导出/结款管线可消费结构。
 */
@Component
public class ClerkRuleCompiler {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final ClerkRuleIndex clerkRuleIndex;

    public ClerkRuleCompiler(ClerkRuleIndex clerkRuleIndex) {
        this.clerkRuleIndex = clerkRuleIndex;
    }

    public ObjectNode compileForCustomer(String customerCode) {
        JsonNode baseline = clerkRuleIndex.baselineForCustomer(customerCode);
        if (baseline == null || !baseline.isObject()) {
            return null;
        }
        ObjectNode compiled = MAPPER.createObjectNode();
        ArrayNode billingPolicies = MAPPER.createArrayNode();
        ArrayNode fixedPrices = MAPPER.createArrayNode();
        ArrayNode exportLayouts = MAPPER.createArrayNode();
        ArrayNode clerkRules = MAPPER.createArrayNode();

        JsonNode rules = baseline.path("rules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                if (!rule.path("isActive").asBoolean(true)) {
                    continue;
                }
                clerkRules.add(rule);
                String ruleType = rule.path("ruleType").asText("");
                String stage = rule.path("stage").asText("bill_export");
                switch (ruleType) {
                    case "DISCOUNT_OVERLAY":
                    case "PIECE_TIER_DISCOUNT":
                        if (stageTargetsBillExport(stage)
                                && !rule.path("params").path("validateOnly").asBoolean(false)) {
                            billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_EXPORT_ONLY));
                        }
                        if (stageTargetsSettlement(stage)) {
                            billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                        }
                        break;
                    case "SETTLEMENT_DISCOUNT":
                        JsonNode settlementParams = rule.path("params");
                        if (settlementParams.has("htRate") && settlementParams.has("ltRate")) {
                            billingPolicies.add(toTemperatureDiscountPolicy(
                                    rule, "HT", settlementParams.path("htRate").asDouble(),
                                    BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                            billingPolicies.add(toTemperatureDiscountPolicy(
                                    rule, "LT", settlementParams.path("ltRate").asDouble(),
                                    BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                        } else {
                            billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                        }
                        break;
                    case "FIXED_PRICE_EXPORT":
                        fixedPrices.add(toExportFixedPrice(rule));
                        break;
                    case "EXPORT_LAYOUT":
                        exportLayouts.add(rule);
                        break;
                    case "SETTLEMENT_MIN_CHARGE":
                        billingPolicies.add(toMonthlySettlementPolicy(rule));
                        break;
                    case "LOGISTICS_FEE":
                    case "LOGISTICS_WAIVE":
                        billingPolicies.add(toLogisticsPolicy(rule));
                        break;
                    case "LOGISTICS_CARD_DEDUCT":
                        billingPolicies.add(toLogisticsCardPolicy(rule));
                        break;
                    case "SETTLEMENT_EXTRA":
                        if (stageTargetsSettlement(stage)) {
                            billingPolicies.add(toSettlementExtraPolicy(rule));
                        }
                        break;
                    case "URGENT":
                        billingPolicies.add(toUrgentPolicy(rule));
                        break;
                    case "MONTHLY_SUPPLEMENT_REPORT":
                        // 保留在 clerkRules；exportSupplementTypes 另收集
                        break;
                    default:
                        break;
                }
            }
        }

        ArrayNode supplementTypes = collectExportSupplementTypes(rules);
        if (!supplementTypes.isEmpty()) {
            compiled.set("exportSupplementTypes", supplementTypes);
        }

        if (!billingPolicies.isEmpty()) {
            compiled.set("billingPolicies", billingPolicies);
        }
        if (!fixedPrices.isEmpty()) {
            ObjectNode specialRules = MAPPER.createObjectNode();
            specialRules.set("fixedPrices", fixedPrices);
            compiled.set("specialRules", specialRules);
        }
        if (!exportLayouts.isEmpty()) {
            compiled.set("exportLayouts", exportLayouts);
        }
        if (!clerkRules.isEmpty()) {
            compiled.set("clerkRules", clerkRules);
        }
        if (baseline.has("attachmentRefs")) {
            compiled.set("attachmentRefs", baseline.get("attachmentRefs"));
        }
        compiled.put("customerCode", baseline.path("customerCode").asText(customerCode));
        return compiled.size() <= 1 ? null : compiled;
    }

    public boolean hasActiveBillExportRules(String customerCode) {
        ObjectNode compiled = compileForCustomer(customerCode);
        if (compiled == null) {
            return false;
        }
        JsonNode policies = compiled.path("billingPolicies");
        if (policies.isArray()) {
            for (JsonNode policy : policies) {
                if (BillingPolicyApplier.stageMatches(policy, BillingPolicyApplier.STAGE_EXPORT_ONLY)) {
                    return true;
                }
            }
        }
        JsonNode fixed = compiled.path("specialRules").path("fixedPrices");
        if (fixed.isArray() && !fixed.isEmpty()) {
            return true;
        }
        JsonNode clerkRules = compiled.path("clerkRules");
        if (clerkRules.isArray()) {
            for (JsonNode rule : clerkRules) {
                String stage = rule.path("stage").asText("bill_export");
                if (!stageTargetsBillExport(stage)) {
                    continue;
                }
                String type = rule.path("ruleType").asText("");
                if ("ZERO_ROW_PACKAGING_FEE".equals(type)
                        || "SEPARATE_PRICING_SYSTEM".equals(type)
                        || "EXPORT_LAYOUT".equals(type)
                        || "FIXED_PRICE_EXPORT".equals(type)
                        || "BILL_EXPORT_PRICE_RULE".equals(type)
                        || "PACK_NAME_PRICE".equals(type)
                        || "PRICE_VALIDATE_ONLY".equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean stageTargetsBillExport(String stage) {
        return "bill_export".equalsIgnoreCase(stage) || "both".equalsIgnoreCase(stage);
    }

    private static boolean stageTargetsSettlement(String stage) {
        return "settlement".equalsIgnoreCase(stage) || "both".equalsIgnoreCase(stage);
    }

    private ObjectNode toTemperatureDiscountPolicy(JsonNode rule, String temperature, double rate, String applyStage) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", rule.path("name").asText("内勤折扣") + "-" + temperature);
        if (rule.has("priority")) {
            policy.put("priority", rule.path("priority").asInt());
        }
        ObjectNode scope = MAPPER.createObjectNode();
        scope.put("temperature", temperature);
        policy.set("scope", scope);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", applyStage);
        params.put("rate", rate);
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toDiscountPolicy(JsonNode rule, String applyStage) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", rule.path("name").asText("内勤折扣"));
        if (rule.has("priority")) {
            policy.put("priority", rule.path("priority").asInt());
        }
        ObjectNode scope = MAPPER.createObjectNode();
        if (rule.has("scope")) {
            scope.setAll((ObjectNode) rule.get("scope"));
        }
        JsonNode ruleParams = rule.path("params");
        if (ruleParams.has("temperature") && !scope.has("temperature")) {
            scope.put("temperature", ruleParams.get("temperature").asText());
        }
        if (scope.isEmpty()) {
            scope.put("temperature", "ANY");
        }
        policy.set("scope", scope);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", applyStage);
        if (ruleParams.isObject()) {
            Iterator<String> fields = ruleParams.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!"temperature".equals(field)) {
                    params.set(field, ruleParams.get(field));
                }
            }
        }
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toMonthlySettlementPolicy(JsonNode rule) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "MONTHLY_SETTLEMENT");
        policy.put("name", rule.path("name").asText("低消"));
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", BillingPolicyApplier.STAGE_SETTLEMENT_ONLY);
        JsonNode ruleParams = rule.path("params");
        if (ruleParams.isObject()) {
            Iterator<String> fields = ruleParams.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                params.set(field, ruleParams.get(field));
            }
        }
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toLogisticsCardPolicy(JsonNode rule) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "LOGISTICS");
        policy.put("name", rule.path("name").asText("物流卡抵扣"));
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", BillingPolicyApplier.STAGE_SETTLEMENT_ONLY);
        params.put("useLogisticsCard", true);
        if (rule.path("params").path("deductFromCard").asBoolean(true)) {
            params.put("cardDeductionEnabled", true);
        }
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toLogisticsPolicy(JsonNode rule) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "LOGISTICS");
        policy.put("name", rule.path("name").asText("物流"));
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", BillingPolicyApplier.STAGE_SETTLEMENT_ONLY);
        JsonNode ruleParams = rule.path("params");
        if (ruleParams.isObject()) {
            Iterator<String> fields = ruleParams.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                params.set(field, ruleParams.get(field));
            }
        }
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toSettlementExtraPolicy(JsonNode rule) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "SETTLEMENT_EXTRA");
        policy.put("name", rule.path("name").asText("结款附加费"));
        if (rule.has("priority")) {
            policy.put("priority", rule.path("priority").asInt());
        }
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", BillingPolicyApplier.STAGE_SETTLEMENT_ONLY);
        JsonNode ruleParams = rule.path("params");
        if (ruleParams.isObject()) {
            Iterator<String> fields = ruleParams.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                params.set(field, ruleParams.get(field));
            }
        }
        policy.set("params", params);
        return policy;
    }

    private ObjectNode toUrgentPolicy(JsonNode rule) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "URGENT");
        policy.put("name", rule.path("name").asText("加急"));
        if (rule.has("priority")) {
            policy.put("priority", rule.path("priority").asInt());
        }
        ObjectNode params = MAPPER.createObjectNode();
        JsonNode ruleParams = rule.path("params");
        if (ruleParams.isObject()) {
            params.setAll((ObjectNode) ruleParams.deepCopy());
        } else {
            params.put("baseMultiplier", 1.25);
            params.put("adjustedMultiplier", 1.25);
        }
        policy.set("params", params);
        return policy;
    }

    private ArrayNode collectExportSupplementTypes(JsonNode rules) {
        ArrayNode types = MAPPER.createArrayNode();
        if (rules == null || !rules.isArray()) {
            return types;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode rule : rules) {
            if (!"MONTHLY_SUPPLEMENT_REPORT".equals(rule.path("ruleType").asText())) {
                continue;
            }
            if (!rule.path("isActive").asBoolean(true)) {
                continue;
            }
            String reportType = sanitizeReportType(rule.path("params").path("reportType").asText("").trim());
            if (!reportType.isBlank() && seen.add(reportType)) {
                types.add(reportType);
            }
        }
        return types;
    }

    private ObjectNode toExportFixedPrice(JsonNode rule) {
        ObjectNode fixed = MAPPER.createObjectNode();
        fixed.put("name", rule.path("name").asText());
        fixed.put("exportApply", true);
        JsonNode params = rule.path("params");
        if (params.isObject()) {
            Iterator<String> fields = params.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                fixed.set(field, params.get(field));
            }
        }
        if (rule.has("scope")) {
            JsonNode scope = rule.get("scope");
            if (scope.has("keywords")) {
                fixed.set("keywords", scope.get("keywords"));
            }
            if (scope.has("acceptedTypes")) {
                fixed.set("acceptedTypes", scope.get("acceptedTypes"));
            }
        }
        return fixed;
    }

    private static String sanitizeReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return "";
        }
        return switch (reportType) {
            case "dept_sterilize_summary" -> "dept_summary";
            case "instrument_count_by_dept" -> "instrument_audit";
            default -> reportType;
        };
    }
}
