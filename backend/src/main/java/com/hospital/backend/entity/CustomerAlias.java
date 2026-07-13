package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerAlias {

    private Long id;

    private Long customerId;

    private String alias;

    private String matchType = "contains";

    private String source = "manual";

    private Integer priority = 100;

    private Boolean isActive = true;

    private LocalDateTime createdAt;
}
