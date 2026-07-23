package com.hospital.backend.dto.response.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hospital.backend.dto.request.customer.CustomerAliasDto;
import com.hospital.backend.dto.request.customer.CustomerDiscountDto;
import com.hospital.backend.dto.request.customer.CustomerPathOverrideDto;
import com.hospital.backend.dto.request.customer.CustomerProductRuleDto;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CustomerResponse {

    private Long id;

    private String code;

    @JsonProperty("canonical_name")
    private String canonicalName;

    private String status;

    @JsonProperty("cap_mode")
    private String capMode;

    @JsonProperty("charge_double_bag_when_capped")
    private Boolean chargeDoubleBagWhenCapped;

    @JsonProperty("default_rule_id")
    private Long defaultRuleId;

    @JsonProperty("billing_enabled")
    private Boolean billingEnabled;

    @JsonProperty("billing_pricing_mode")
    private String billingPricingMode;

    @JsonProperty("path_override")
    private CustomerPathOverrideDto pathOverride;

    @JsonProperty("export_name_mapping")
    private String exportNameMapping;

    @JsonProperty("standard_pricing_override")
    private String standardPricingOverride;

    private String notes;

    private List<CustomerAliasDto> aliases;

    private List<CustomerDiscountDto> discounts;

    @JsonProperty("product_rules")
    private List<CustomerProductRuleDto> productRules;

    @JsonProperty("alias_count")
    private Integer aliasCount;

    @JsonProperty("department_count")
    private Integer departmentCount;

    @JsonProperty("physician_count")
    private Integer physicianCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
