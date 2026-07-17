package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Customer extends BaseEntity {

    private String code;

    private String canonicalName;

    private String status = "active";

    private String capMode;

    private Boolean chargeDoubleBagWhenCapped = false;

    private Long defaultRuleId;

    private Boolean billingEnabled = false;

    /** standard / special_only / hybrid */
    private String billingPricingMode = "standard";

    /** JSON: { "disableLowTemp": true, "forceHighTempUnitPrice": 3 } */
    private String pathOverride;

    /** JSON: { "原包名": "导出包名" } — FR-M1-09 */
    private String exportNameMapping;

    private String notes;
}
