package com.hospital.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HospitalPricingRule extends BaseEntity {

    private String name;

    private String version;

    private String hospitalName;

    @JsonProperty("plan_name")
    private String planName;

    private String description;

    @JsonProperty("is_active")
    private Boolean isActive = false;

    @JsonProperty("rules_json")
    private String rulesJson;
}
