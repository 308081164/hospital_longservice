package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerBillingRuleGroup {

    private Long id;

    private Long customerId;

    private String groupCode = "default";

    private String groupName = "默认规则组";

    private String rulesJson;

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
