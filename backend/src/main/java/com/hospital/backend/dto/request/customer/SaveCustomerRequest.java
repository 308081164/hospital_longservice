package com.hospital.backend.dto.request.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SaveCustomerRequest {

    @NotBlank(message = "客户编码不能为空")
    private String code;

    @NotBlank(message = "客户名称不能为空")
    private String canonicalName;

    private String status;

    private String capMode;

    private Boolean chargeDoubleBagWhenCapped;

    private Long defaultRuleId;

    private Boolean billingEnabled;

    /** standard / special_only / hybrid */
    private String billingPricingMode;

    private CustomerPathOverrideDto pathOverride;

    /** JSON object string: { "原包名": "导出包名" } */
    private String exportNameMapping;

    /** JSON object: merges into DefaultPricingTemplate (highTemperature / lowTemperature / dressingPack) */
    private String standardPricingOverride;

    private String notes;

    private List<CustomerAliasDto> aliases;

    private List<CustomerDiscountDto> discounts;

    private List<CustomerProductRuleDto> productRules;
}
