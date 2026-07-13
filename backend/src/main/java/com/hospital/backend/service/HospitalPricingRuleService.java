package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.hospital.BatchNeedleKeywordsRequest;
import com.hospital.backend.dto.request.hospital.SavePricingRuleRequest;
import com.hospital.backend.dto.request.hospital.ShadowCompareRequest;
import com.hospital.backend.dto.response.hospital.PricingRuleResponse;
import com.hospital.backend.dto.response.hospital.PricingRuleRevisionResponse;

import java.util.List;
import java.util.Map;

public interface HospitalPricingRuleService {

    Result<List<PricingRuleResponse>> listRules(String hospitalName);

    Result<PricingRuleResponse> getActiveRule(String hospitalName);

    Result<PricingRuleResponse> resolveRule(String hospitalName);

    Result<PricingRuleResponse> createRule(SavePricingRuleRequest request);

    Result<PricingRuleResponse> updateRule(Long id, SavePricingRuleRequest request);

    Result<Map<String, Boolean>> deleteRule(Long id);

    Result<List<PricingRuleRevisionResponse>> listRevisions(Long ruleId);

    Result<PricingRuleResponse> rollbackRevision(Long ruleId, Long revisionId, String operator);

    Result<PricingRuleResponse> batchUpdateNeedleKeywords(Long ruleId, BatchNeedleKeywordsRequest request);

    Result<Map<String, Object>> shadowCompare(ShadowCompareRequest request);
}
