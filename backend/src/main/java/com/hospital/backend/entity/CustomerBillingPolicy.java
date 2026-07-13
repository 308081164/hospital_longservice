package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerBillingPolicy {

    private Long id;

    private Long customerId;

    private String policyType;

    private String name;

    /** JSON: { "temperature": "HT|LT|ANY" } */
    private String scope;

    /** JSON: { "rate": 0.7, "skipWhenFixedPrice": true, "feePerTrip": 80.5 } */
    private String params;

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
