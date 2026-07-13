package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HospitalPricingRuleRevision {

    private Long id;

    private Long ruleId;

    private String version;

    private String rulesJson;

    private String createdBy;

    private LocalDateTime createdAt;
}
