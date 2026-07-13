package com.hospital.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HospitalReconciliationJob extends BaseEntity {

    @JsonProperty("hospital_name")
    private String hospitalName;

    @JsonProperty("source_file_name")
    private String sourceFileName;

    @JsonProperty("source_file_path")
    private String sourceFilePath;

    @JsonProperty("source_file_size")
    private Long sourceFileSize;

    @JsonProperty("rule_id")
    private Long ruleId;

    @JsonProperty("rule_name")
    private String ruleName;

    @JsonProperty("plan_name")
    private String planName;

    @JsonProperty("rule_version")
    private String ruleVersion;

    @JsonProperty("version_no")
    private Integer versionNo;

    @JsonProperty("total_rows")
    private Integer totalRows = 0;

    @JsonProperty("corrected_rows")
    private Integer correctedRows = 0;

    @JsonProperty("unchanged_rows")
    private Integer unchangedRows = 0;

    @JsonProperty("warning_rows")
    private Integer warningRows = 0;

    @JsonProperty("skipped_rows")
    private Integer skippedRows = 0;

    @JsonProperty("total_difference")
    private Double totalDifference = 0.0;

    @JsonProperty("review_status")
    private String reviewStatus = "pending";

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonProperty("operator_name")
    private String operatorName;

    @JsonProperty("reviewer_name")
    private String reviewerName;

    @JsonProperty("rows_json")
    private String rowsJson;

    @JsonProperty("source_date_range")
    private String sourceDateRange;

    @JsonProperty("sheet_names")
    private String sheetNames;

    @JsonProperty("sheet_row_counts")
    private String sheetRowCounts;

    @JsonProperty("sheet_warning_counts")
    private String sheetWarningCounts;

    @JsonProperty("logistics_trip_count")
    private Integer logisticsTripCount;

    @JsonProperty("logistics_fee")
    private Double logisticsFee;

    @JsonProperty("logistics_breakdown")
    private String logisticsBreakdown;

    @JsonProperty("settlement_adjustment")
    private Double settlementAdjustment;

    @JsonProperty("monthly_breakdown")
    private String monthlyBreakdown;

    @JsonProperty("original_total_price")
    private Double originalTotalPrice = 0.0;

    @JsonProperty("corrected_total_price")
    private Double correctedTotalPrice = 0.0;
}
