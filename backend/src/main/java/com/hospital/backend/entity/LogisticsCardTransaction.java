package com.hospital.backend.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogisticsCardTransaction extends BaseEntity {

    private Long cardId;

    /** RECHARGE | DEDUCT | ADJUST */
    private String transactionType;

    private Double amount;

    private Double balanceAfter;

    private Long jobId;

    private String remark;
}
