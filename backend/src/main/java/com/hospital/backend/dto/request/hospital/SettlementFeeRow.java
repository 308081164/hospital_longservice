package com.hospital.backend.dto.request.hospital;

import lombok.Data;

@Data
public class SettlementFeeRow {

    private String indexLabel;

    private String itemLabel;

    private Double amount;

    private String remark;
}
