package com.hospital.backend.dto.response.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PricingRuleInfoResponse {

    @JsonProperty("customer_code")
    private String customerCode;

    @JsonProperty("disabled_categories")
    private List<String> disabledCategories;

    private List<CategoryToggle> toggles;

    @Getter
    @Setter
    public static class CategoryToggle {

        /** clerk_price | clerk_discount */
        private String category;

        private String label;

        private boolean enabled;

        /** 该院是否存在该层内勤规则 */
        private boolean available;

        @JsonProperty("rule_count")
        private int ruleCount;
    }
}
