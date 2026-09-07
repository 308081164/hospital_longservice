package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerProductRuleTombstone {

    private Long id;

    private Long productRuleId;

    private Long customerId;

    private String customerCode;

    private String ruleName;

    private String ruleSnapshot;

    private String manifestHash;

    private String quarantineReason;

    private LocalDateTime quarantinedAt;

    private LocalDateTime restoredAt;

    private String restoredBy;
}
