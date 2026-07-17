package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BillingRuleChangeLog {

    private Long id;

    private Long customerId;

    private Long ruleGroupId;

    private Long productRuleId;

    private String changeType;

    private String entityType = "PRODUCT_RULE";

    private String beforeSnapshot;

    private String afterSnapshot;

    private String operatorName;

    private String changeSummary;

    private LocalDateTime createdAt;
}
