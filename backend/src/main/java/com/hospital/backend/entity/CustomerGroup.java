package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerGroup extends BaseEntity {

    private String name;

    /** settlement_merge | logistics_merge */
    private String groupType;

    private String config;

    private Boolean isActive = true;
}
