package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.hospital.BatchNeedleKeywordsRequest;
import com.hospital.backend.dto.request.hospital.SavePricingRuleRequest;
import com.hospital.backend.dto.request.hospital.ShadowCompareRequest;
import com.hospital.backend.dto.response.hospital.PricingRuleResponse;
import com.hospital.backend.dto.response.hospital.PricingRuleRevisionResponse;
import com.hospital.backend.service.HospitalPricingRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HospitalPricingRuleController {

    private final HospitalPricingRuleService hospitalPricingRuleService;

    @GetMapping("/hospital-pricing-rules")
    public Result<List<PricingRuleResponse>> listRules(
            @RequestParam(required = false) String hospitalName) {
        return hospitalPricingRuleService.listRules(hospitalName);
    }

    @GetMapping("/hospital-pricing-rules/active")
    public Result<PricingRuleResponse> getActiveRule(
            @RequestParam(required = false) String hospitalName) {
        return hospitalPricingRuleService.getActiveRule(hospitalName);
    }

    @GetMapping("/hospital-pricing-rules/resolve")
    public Result<PricingRuleResponse> resolveRule(@RequestParam String hospitalName) {
        return hospitalPricingRuleService.resolveRule(hospitalName);
    }

    @PostMapping("/hospital-pricing-rules")
    public Result<PricingRuleResponse> createRule(@Valid @RequestBody SavePricingRuleRequest request) {
        return hospitalPricingRuleService.createRule(request);
    }

    @PutMapping("/hospital-pricing-rules/{id}")
    public Result<PricingRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody SavePricingRuleRequest request) {
        return hospitalPricingRuleService.updateRule(id, request);
    }

    @DeleteMapping("/hospital-pricing-rules/{id}")
    public Result<Map<String, Boolean>> deleteRule(@PathVariable Long id) {
        return hospitalPricingRuleService.deleteRule(id);
    }

    @GetMapping("/hospital-pricing-rules/{id}/revisions")
    public Result<List<PricingRuleRevisionResponse>> listRevisions(@PathVariable Long id) {
        return hospitalPricingRuleService.listRevisions(id);
    }

    @PostMapping("/hospital-pricing-rules/{id}/revisions/{revisionId}/rollback")
    public Result<PricingRuleResponse> rollbackRevision(
            @PathVariable Long id,
            @PathVariable Long revisionId,
            @RequestParam(required = false) String operator) {
        return hospitalPricingRuleService.rollbackRevision(id, revisionId, operator);
    }

    @PutMapping("/hospital-pricing-rules/{id}/needle-keywords/batch")
    public Result<PricingRuleResponse> batchUpdateNeedleKeywords(
            @PathVariable Long id,
            @Valid @RequestBody BatchNeedleKeywordsRequest request) {
        return hospitalPricingRuleService.batchUpdateNeedleKeywords(id, request);
    }

    @PostMapping("/pricing/shadow-compare")
    public Result<Map<String, Object>> shadowCompare(@Valid @RequestBody ShadowCompareRequest request) {
        return hospitalPricingRuleService.shadowCompare(request);
    }
}
