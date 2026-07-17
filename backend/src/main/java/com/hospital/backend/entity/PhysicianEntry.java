package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhysicianEntry extends BaseEntity {

    private Long customerId;

    private String physicianName;

    private Long departmentEntryId;

    private String departmentName;

    private String code;

    private String notes;

    private Integer usageCount = 0;

    private Boolean isActive = true;
}
