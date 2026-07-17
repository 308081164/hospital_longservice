package com.hospital.backend.dto.response.export;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExportValidationResponse {

    private Long jobId;
    private int totalRows;
    private int warningRows;
    private int correctedRows;
    private Double totalDifference;
    private Double logisticsFee;
    private Double settlementAdjustment;
    private Double settlementTotal;
    private Double externalInstrumentTotal;
    private Boolean settlementReconciled;
    private Boolean allocationBalanced;
    private boolean ready;
    private String message;
}
