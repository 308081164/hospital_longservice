package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.ClerkRuleIndex;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * 将内勤规则 baseline 编译为导出管线可消费的 billingPolicies / specialRules 结构。
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

        JsonNode rules = baseline.path("rules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                if (!rule.path("isActive").asBoolean(true)) {
                    continue;
                }
                String ruleType = rule.path("ruleType").asText("");
                String stage = rule.path("stage").asText("bill_export");
                switch (ruleType) {
                    case "DISCOUNT_OVERLAY":
                    case "PIECE_TIER_DISCOUNT":
                        if (stageTargetsBillExport(stage)) {
                            billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_EXPORT_ONLY));
                        }
                        if (stageTargetsSettlement(stage)) {
                            billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                        }
                        break;
                    case "SETTLEMENT_DISCOUNT":
                        billingPolicies.add(toDiscountPolicy(rule, BillingPolicyApplier.STAGE_SETTLEMENT_ONLY));
                        break;
                    case "FIXED_PRICE_EXPORT":
                        fixedPrices.add(toExportFixedPrice(rule));
                        break;
                    case "EXPORT_LAYOUT":
                        exportLayouts.add(rule);
                        break;
                    default:
                        break;
                }
            }
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
        compiled.put("customerCode", baseline.path("customerCode").asText(customerCode));
        return compiled.isEmpty() ? null : compiled;
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
        return fixed.isArray() && !fixed.isEmpty();
    }

    private static boolean stageTargetsBillExport(String stage) {
        return "bill_export".equalsIgnoreCase(stage) || "both".equalsIgnoreCase(stage);
    }

    private static boolean stageTargetsSettlement(String stage) {
        return "settlement".equalsIgnoreCase(stage) || "both".equalsIgnoreCase(stage);
    }

    private ObjectNode toDiscountPolicy(JsonNode rule, String applyStage) {
        ObjectNode policy = MAPPER.createObjectNode();
        policy.put("policyType", "DISCOUNT");
        policy.put("name", rule.path("name").asText("内勤折扣"));
        if (rule.has("priority")) {
            policy.put("priority", rule.path("priority").asInt());
        }
        if (rule.has("scope")) {
            policy.set("scope", rule.get("scope"));
        } else {
            policy.putObject("scope").put("temperature", "ANY");
        }
        ObjectNode params = MAPPER.createObjectNode();
        params.put("applyStage", applyStage);
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
}
