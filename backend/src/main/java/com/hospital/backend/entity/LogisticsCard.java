package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogisticsCard extends BaseEntity {

    private Long customerId;

    private String name;

    private Double balance;

    private Double initialBalance;

    private Boolean isActive = true;
}
