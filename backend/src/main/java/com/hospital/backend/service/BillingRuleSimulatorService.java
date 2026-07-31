package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.billing.BillingRuleSimulateRequest;
import com.hospital.backend.dto.response.billing.BillingRuleSimulateResponse;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.CustomerProductRuleMapper;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingRuleSimulatorService {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private final CustomerMapper customerMapper;
    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final PricingRuleCompiler pricingRuleCompiler;
    private final ProductMatchService productMatchService;
    private final CustomerProductRuleMapper productRuleMapper;

    public Result<BillingRuleSimulateResponse> simulate(BillingRuleSimulateRequest request) {
        Customer customer = customerMapper.selectById(request.getCustomerId());
        if (customer == null) {
            return Result.fail(404, "客户不存在");
        }

        Long ruleId = request.getRuleId() != null ? request.getRuleId() : customer.getDefaultRuleId();
        if (ruleId == null) {
            return Result.fail(400, "客户未绑定默认计价规则，请指定 ruleId");
        }

        HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(ruleId);
        if (ruleEntity == null && customer.getDefaultRuleId() != null
                && !customer.getDefaultRuleId().equals(ruleId)) {
            ruleEntity = pricingRuleMapper.selectById(customer.getDefaultRuleId());
        }
        if (ruleEntity == null) {
            ruleEntity = pricingRuleMapper.selectById(1L);
        }
        if (ruleEntity == null) {
            return Result.fail(404, "计价规则不存在");
        }

        try {
            JsonNode baseRules = MAPPER.readTree(ruleEntity.getRulesJson());
            String hospitalName = request.getHospitalName();
            if (hospitalName == null || hospitalName.isBlank()) {
                hospitalName = customer.getCanonicalName();
            }
            JsonNode compiled = pricingRuleCompiler.compileForCustomer(baseRules, customer, hospitalName);
            PricingEngine engine = new PricingEngine(compiled);
            engine.enableStructuredProductMatch(productMatchService);

            Map<String, Object> row = new HashMap<>(request.getSampleRow());
            row.put("hospitalName", hospitalName);
            productMatchService.matchRow(row).ifPresent(match -> {
                row.put("matchedProductId", match.getProductId());
                if (match.getVariantId() != null) {
                    row.put("matchedVariantId", match.getVariantId());
                }
            });

            PricingEngine.ProcessedResult result = engine.processRow(row);
            List<Map<String, Object>> matchChain = buildMatchChain(compiled, row, result);

            BillingPolicyApplier.AppliedDiscount discount = BillingPolicyApplier.resolveBestDiscount(
                    compiled,
                    str(row, "type"),
                    str(row, "packName"),
                    str(row, "packageMaterial"),
                    hospitalName,
                    false);

            BillingRuleSimulateResponse response = BillingRuleSimulateResponse.builder()
                    .expectedUnitPrice(result.expectedUnitPrice)
                    .correctedTotalPrice(result.correctedTotalPrice)
                    .difference(result.difference)
                    .status(result.status)
                    .pricingRule(result.pricingRule)
                    .matchedRuleId(result.matchedRuleId)
                    .matchedPriceOption(result.matchedPriceOption)
                    .notes(result.notes)
                    .policyTraces(discount.trace())
                    .matchChain(matchChain)
                    .build();
            return Result.success(response);
        } catch (Exception e) {
            return Result.fail(500, "试算失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildMatchChain(
            JsonNode compiled,
            Map<String, Object> row,
            PricingEngine.ProcessedResult result) {
        List<Map<String, Object>> chain = new ArrayList<>();
        JsonNode fixedPrices = compiled.path("specialRules").path("fixedPrices");
        if (fixedPrices.isArray() && !fixedPrices.isEmpty()) {
            Map<String, Object> compiledStep = new LinkedHashMap<>();
            compiledStep.put("step", "compiled_fixed_prices");
            compiledStep.put("count", fixedPrices.size());
            List<String> names = new ArrayList<>();
            for (JsonNode rule : fixedPrices) {
                names.add(rule.path("name").asText());
            }
            compiledStep.put("names", names);
            chain.add(compiledStep);
        }
        if (result.matchedRuleId != null) {
            CustomerProductRule rule = productRuleMapper.selectById(result.matchedRuleId);
            if (rule != null) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("step", "special_rule");
                hit.put("ruleId", rule.getId());
                hit.put("ruleName", rule.getName());
                hit.put("ruleType", rule.getRuleType());
                chain.add(hit);
            }
        }
        Map<String, Object> engineStep = new LinkedHashMap<>();
        engineStep.put("step", "pricing_engine");
        engineStep.put("pricingRule", result.pricingRule);
        engineStep.put("status", result.status);
        chain.add(engineStep);

        JsonNode policies = compiled.path("billingPolicies");
        if (policies.isArray() && !policies.isEmpty()) {
            Map<String, Object> policyStep = new LinkedHashMap<>();
            policyStep.put("step", "billing_policies");
            policyStep.put("count", policies.size());
            chain.add(policyStep);
        }
        return chain;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
