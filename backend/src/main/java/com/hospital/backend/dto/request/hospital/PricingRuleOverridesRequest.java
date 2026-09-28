package com.hospital.backend.dto.request.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PricingRuleOverridesRequest {

    /** 禁用的内勤规则层：clerk_price | clerk_discount */
    @JsonProperty("disabledCategories")
    private List<String> disabledCategories;
}
