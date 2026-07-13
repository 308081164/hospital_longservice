package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.service.PricingRuleCompiler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PricingRuleCompiler pricingRuleCompiler;

    @GetMapping("/default-pricing-template")
    public Result<Map<String, Object>> getDefaultPricingTemplate() {
        return Result.success(pricingRuleCompiler.defaultTemplateMap());
    }
}
