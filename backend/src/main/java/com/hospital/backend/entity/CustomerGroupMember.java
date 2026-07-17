package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerGroupMember extends BaseEntity {

    private Long groupId;

    private Long customerId;

    private Double shareRatio;
}
