package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.hospital.BatchNeedleKeywordsRequest;
import com.hospital.backend.dto.request.hospital.SavePricingRuleRequest;
import com.hospital.backend.dto.request.hospital.ShadowCompareRequest;
import com.hospital.backend.dto.response.hospital.PricingRuleResponse;
import com.hospital.backend.dto.response.hospital.PricingRuleRevisionResponse;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.entity.HospitalPricingRuleRevision;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.mapper.HospitalPricingRuleRevisionMapper;
import com.hospital.backend.service.HospitalPricingRuleService;
import com.hospital.backend.service.PricingEngine;
import com.hospital.backend.service.PricingRuleCompiler;
import com.hospital.backend.service.ProductMatchService;
import com.hospital.backend.service.RuleSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalPricingRuleServiceImpl implements HospitalPricingRuleService {

    private final HospitalPricingRuleMapper pricingRuleMapper;
    private final HospitalPricingRuleRevisionMapper revisionMapper;
    private final RuleSchemaValidator ruleSchemaValidator;
    private final PricingRuleCompiler pricingRuleCompiler;
    private final ProductMatchService productMatchService;

    @Override
    public Result<List<PricingRuleResponse>> listRules(String hospitalName) {
        List<HospitalPricingRule> rules;
        if (hospitalName != null && !hospitalName.isEmpty()) {
            rules = pricingRuleMapper.selectByHospitalNameOrderByUpdatedAtDesc(hospitalName);
        } else {
            rules = pricingRuleMapper.selectAllOrderByUpdatedAtDesc();
        }

        List<PricingRuleResponse> data = rules.stream()
                .map(this::toPricingRuleResponse)
                .toList();

        return Result.success(data);
    }

    @Override
    public Result<PricingRuleResponse> getActiveRule(String hospitalName) {
        HospitalPricingRule rule = null;

        if (hospitalName != null && !hospitalName.isEmpty()) {
            rule = pricingRuleMapper.selectByIsActiveTrueAndHospitalName(hospitalName);

            if (rule == null) {
                List<HospitalPricingRule> hospitalRules = pricingRuleMapper
                        .selectByHospitalNameOrderByUpdatedAtDesc(hospitalName);
                if (!hospitalRules.isEmpty()) {
                    rule = hospitalRules.get(0);
                }
            }
        }

        if (rule == null) {
            rule = pricingRuleMapper.selectByIsActiveTrue();
        }

        if (rule == null) {
            return Result.fail(404, "没有激活的计费规则");
        }

        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    public Result<PricingRuleResponse> resolveRule(String hospitalName) {
        if (hospitalName.isBlank()) {
            return Result.fail(400, "医院名称不能为空");
        }

        HospitalPricingRule rule = null;

        rule = pricingRuleMapper.selectByIsActiveTrueAndHospitalName(hospitalName);
        if (rule == null) {
            List<HospitalPricingRule> hospitalRules = pricingRuleMapper
                    .selectByHospitalNameOrderByUpdatedAtDesc(hospitalName);
            if (!hospitalRules.isEmpty()) {
                rule = hospitalRules.get(0);
            }
        }

        if (rule == null) {
            List<HospitalPricingRule> planRules = pricingRuleMapper
                    .selectByPlanNameOrderByUpdatedAtDesc(hospitalName);
            if (!planRules.isEmpty()) {
                rule = planRules.get(0);
            }
        }

        if (rule == null) {
            List<HospitalPricingRule> nameMatchRules = pricingRuleMapper
                    .selectByMultiFieldContainingOrderByIsActiveDescUpdatedAtDesc(hospitalName);
            if (!nameMatchRules.isEmpty()) {
                rule = nameMatchRules.get(0);
            }

            if (rule == null && hospitalName.length() > 4) {
                String shortened = hospitalName.replaceAll("[\\d年月日\\-_\\.\\s]+$", "").trim();
                if (shortened.length() < hospitalName.length() && shortened.length() >= 2) {
                    List<HospitalPricingRule> shortMatchRules = pricingRuleMapper
                            .selectByMultiFieldContainingOrderByIsActiveDescUpdatedAtDesc(shortened);
                    if (!shortMatchRules.isEmpty()) {
                        rule = shortMatchRules.get(0);
                    }
                }
            }

            if (rule == null && hospitalName.length() > 4) {
                String stripped = hospitalName.replaceAll("^\\d{4}[\\s_-]?", "").trim();
                if (!stripped.equals(hospitalName) && stripped.length() >= 2) {
                    List<HospitalPricingRule> strippedMatchRules = pricingRuleMapper
                            .selectByMultiFieldContainingOrderByIsActiveDescUpdatedAtDesc(stripped);
                    if (!strippedMatchRules.isEmpty()) {
                        rule = strippedMatchRules.get(0);
                    }
                }
            }
        }

        if (rule == null) {
            rule = pricingRuleMapper.selectByIsActiveTrue();
        }

        if (rule == null) {
            return Result.fail(404, "没有匹配的计费规则，请先创建并激活计费规则");
        }

        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    @Transactional
    public Result<PricingRuleResponse> createRule(SavePricingRuleRequest request) {
        Result<Void> validation = validateRules(request.getRules());
        if (validation.getCode() != 200) {
            return Result.fail(validation.getCode(), validation.getMsg());
        }

        HospitalPricingRule rule = new HospitalPricingRule();
        rule.setName(request.getName());
        rule.setVersion(request.getVersion());
        rule.setDescription(request.getDescription());
        rule.setHospitalName(request.getHospitalName());
        rule.setPlanName(request.getPlanName());
        rule.setIsActive(false);
        rule.setRulesJson(JsonUtils.toJson(request.getRules()));

        pricingRuleMapper.insert(rule);
        saveRevision(rule, request.getCreatedBy());
        productMatchService.refreshCache();
        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    @Transactional
    public Result<PricingRuleResponse> updateRule(Long id, SavePricingRuleRequest request) {
        HospitalPricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            return Result.fail(404, "规则不存在");
        }

        if (request.getRules() != null) {
            Result<Void> validation = validateRules(request.getRules());
            if (validation.getCode() != 200) {
                return Result.fail(validation.getCode(), validation.getMsg());
            }
        }

        if (request.getName() != null) {
            rule.setName(request.getName());
        }
        if (request.getVersion() != null) {
            rule.setVersion(request.getVersion());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getHospitalName() != null) {
            rule.setHospitalName(request.getHospitalName());
        }
        if (request.getPlanName() != null) {
            rule.setPlanName(request.getPlanName());
        }
        if (request.getRules() != null) {
            rule.setRulesJson(JsonUtils.toJson(request.getRules()));
        }

        pricingRuleMapper.updateById(rule);
        if (request.getRules() != null) {
            saveRevision(rule, request.getCreatedBy());
        }
        productMatchService.refreshCache();
        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    public Result<Map<String, Boolean>> deleteRule(Long id) {
        if (!pricingRuleMapper.existsById(id)) {
            return Result.fail(404, "规则不存在");
        }

        pricingRuleMapper.deleteById(id);

        Map<String, Boolean> result = new HashMap<>();
        result.put("success", true);
        return Result.success(result);
    }

    @Override
    public Result<List<PricingRuleRevisionResponse>> listRevisions(Long ruleId) {
        if (!pricingRuleMapper.existsById(ruleId)) {
            return Result.fail(404, "规则不存在");
        }
        List<PricingRuleRevisionResponse> data = revisionMapper.selectByRuleId(ruleId).stream()
                .map(this::toRevisionResponse)
                .toList();
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result<PricingRuleResponse> rollbackRevision(Long ruleId, Long revisionId, String operator) {
        HospitalPricingRule rule = pricingRuleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.fail(404, "规则不存在");
        }
        HospitalPricingRuleRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null || !ruleId.equals(revision.getRuleId())) {
            return Result.fail(404, "版本记录不存在");
        }

        Map<String, Object> rules = JsonUtils.parseToMap(revision.getRulesJson());
        Result<Void> validation = validateRules(rules);
        if (validation.getCode() != 200) {
            return Result.fail(validation.getCode(), validation.getMsg());
        }

        rule.setRulesJson(revision.getRulesJson());
        if (revision.getVersion() != null) {
            rule.setVersion(revision.getVersion());
        }
        pricingRuleMapper.updateById(rule);
        saveRevision(rule, operator);
        productMatchService.refreshCache();
        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    @Transactional
    public Result<PricingRuleResponse> batchUpdateNeedleKeywords(Long ruleId, BatchNeedleKeywordsRequest request) {
        HospitalPricingRule rule = pricingRuleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.fail(404, "规则不存在");
        }

        Map<String, Object> rules = JsonUtils.parseToMap(rule.getRulesJson());
        if (rules == null) {
            rules = new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> needle = rules.computeIfAbsent("needle", k -> new LinkedHashMap<>()) instanceof Map
                ? (Map<String, Object>) rules.get("needle")
                : new LinkedHashMap<>();
        needle.put("keywords", new ArrayList<>(request.getKeywords()));

        Result<Void> validation = validateRules(rules);
        if (validation.getCode() != 200) {
            return Result.fail(validation.getCode(), validation.getMsg());
        }

        rule.setRulesJson(JsonUtils.toJson(rules));
        pricingRuleMapper.updateById(rule);
        saveRevision(rule, request.getOperator());
        productMatchService.refreshCache();
        return Result.success(toPricingRuleResponse(rule));
    }

    @Override
    public Result<Map<String, Object>> shadowCompare(ShadowCompareRequest request) {
        HospitalPricingRule production = pricingRuleMapper.selectById(request.getProductionRuleId());
        if (production == null) {
            return Result.fail(404, "生产规则不存在");
        }

        JsonNode productionRules = parseRulesNode(production.getRulesJson());
        JsonNode draftRules;
        if (request.getDraftRuleId() != null) {
            HospitalPricingRule draft = pricingRuleMapper.selectById(request.getDraftRuleId());
            if (draft == null) {
                return Result.fail(404, "草稿规则不存在");
            }
            draftRules = parseRulesNode(draft.getRulesJson());
        } else if (request.getDraftRules() != null) {
            draftRules = JsonUtils.getObjectMapper().valueToTree(request.getDraftRules());
        } else {
            return Result.fail(400, "请提供 draftRuleId 或 draftRules");
        }

        String hospitalName = request.getHospitalName() != null ? request.getHospitalName() : "";
        JsonNode compiledProduction = pricingRuleCompiler.compile(productionRules, hospitalName);
        JsonNode compiledDraft = pricingRuleCompiler.compile(draftRules, hospitalName);

        PricingEngine productionEngine = new PricingEngine(compiledProduction);
        PricingEngine draftEngine = new PricingEngine(compiledDraft);

        List<Map<String, Object>> diffs = new ArrayList<>();
        int changed = 0;
        for (Map<String, Object> sample : request.getSampleRows()) {
            PricingEngine.ProcessedResult prod = productionEngine.processRow(sample);
            PricingEngine.ProcessedResult draft = draftEngine.processRow(sample);
            boolean priceDiff = !Objects.equals(prod.expectedUnitPrice, draft.expectedUnitPrice)
                    || !Objects.equals(prod.correctedTotalPrice, draft.correctedTotalPrice);
            if (priceDiff) {
                changed++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("packName", sample.get("packName"));
            item.put("productionUnitPrice", prod.expectedUnitPrice);
            item.put("draftUnitPrice", draft.expectedUnitPrice);
            item.put("productionTotal", prod.correctedTotalPrice);
            item.put("draftTotal", draft.correctedTotalPrice);
            item.put("changed", priceDiff);
            diffs.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleCount", request.getSampleRows().size());
        result.put("changedCount", changed);
        result.put("diffs", diffs);
        return Result.success(result);
    }

    private JsonNode parseRulesNode(String rulesJson) {
        try {
            return JsonUtils.getObjectMapper().readTree(rulesJson);
        } catch (Exception e) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
    }

    private Result<Void> validateRules(Map<String, Object> rules) {
        RuleSchemaValidator.ValidationResult result = ruleSchemaValidator.validate(rules);
        if (!result.valid()) {
            return Result.fail(400, "规则校验失败：" + result.message());
        }
        return Result.success(null);
    }

    private void saveRevision(HospitalPricingRule rule, String createdBy) {
        HospitalPricingRuleRevision revision = new HospitalPricingRuleRevision();
        revision.setRuleId(rule.getId());
        revision.setVersion(rule.getVersion());
        revision.setRulesJson(rule.getRulesJson());
        revision.setCreatedBy(createdBy);
        revisionMapper.insert(revision);
    }

    private PricingRuleRevisionResponse toRevisionResponse(HospitalPricingRuleRevision revision) {
        return new PricingRuleRevisionResponse(
                revision.getId(),
                revision.getRuleId(),
                revision.getVersion(),
                revision.getCreatedBy(),
                revision.getCreatedAt()
        );
    }

    private PricingRuleResponse toPricingRuleResponse(HospitalPricingRule rule) {
        Map<String, Object> rules = JsonUtils.parseToMap(rule.getRulesJson());
        if (rules == null) {
            rules = new LinkedHashMap<>();
        }

        return new PricingRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getVersion(),
                rule.getDescription(),
                rule.getIsActive(),
                rule.getHospitalName(),
                rule.getPlanName(),
                rules,
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
