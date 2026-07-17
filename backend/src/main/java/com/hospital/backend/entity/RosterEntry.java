package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RosterEntry extends BaseEntity {

    private Long customerId;

    private String doctorName;

    private String department;

    private String surgicalRoom;

    private String notes;

    private Boolean isActive = true;
}
