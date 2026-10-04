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
    /** 规则单价与原单价不一致且修正总价未按规则落库的行数（导出可能仍用原价） */
    private int unpersistedRulePriceRows;
    private Double totalDifference;
    private Double logisticsFee;
    private Double settlementAdjustment;
    private Double settlementTotal;
    private Double externalInstrumentTotal;
    private Boolean settlementReconciled;
    private Boolean allocationBalanced;
    private boolean ready;
    private String message;
    private Boolean billingEnabled;
    private String billLayout;
    private String d8DisplaySource;
    private String exportProfileLabel;
    private String expectedSheetMode;
    private String strategyKey;
    private Integer distinctSheetCount;
    /** true when dept_split configured but job has only one sheet group */
    private Boolean layoutMismatchWarning;
}
