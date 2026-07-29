package com.hospital.backend.dto.response.hospital;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ReconciliationJobResponse {

    private Long id;

    private String hospitalName;

    private String sourceFileName;

    private String sourceFilePath;

    private Long sourceFileSize;

    private Long ruleId;

    private String ruleName;

    private String planName;

    private String ruleVersion;

    private Integer versionNo;

    private Integer totalRows;

    private Integer correctedRows;

    private Integer unchangedRows;

    private Integer warningRows;

    private Integer skippedRows;

    private Double totalDifference;

    private String reviewStatus;

    private String reviewComment;

    private String operatorName;

    private String reviewerName;

    /** 导入表格第4行的原始日期文本 */
    private String sourceDateRange;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<ReconciliationExportLogResponse> exports;

    private List<Map<String, Object>> rows;

    private List<String> sheetNames;

    /** 按 sheetName 统计的行数（key=科室名，value=该科室的行数） */
    private Map<String, Integer> sheetRowCounts;

    /** 按 sheetName 统计的异常行数（key=科室名，value=该科室的异常行数） */
    private Map<String, Integer> sheetWarningCounts;

    /** 物流次数（唯一发货单号数量） */
    private Integer logisticsTripCount;

    /** 物流费总额（次数 × 单价） */
    private Double logisticsFee;

    /** 物流费明细：趟次、单价、总额、来源 */
    private Map<String, Object> logisticsBreakdown;

    /** 月度结算调整额（低消/封顶后与灭菌费合计的差额） */
    private Double settlementAdjustment;

    /** 月度结算明细 */
    private Map<String, Object> monthlyBreakdown;

    /** 加急费明细 */
    private Map<String, Object> urgentBreakdown;

    /** 设备抵扣明细 */
    private Map<String, Object> deductionBreakdown;

    /** 全部行原始总价汇总 */
    private Double originalTotalPrice;

    /** 全部行修正总价汇总 */
    private Double correctedTotalPrice;

    /** 该医院可用的导出类型（bill / settlement / dept_summary / …） */
    private List<String> exportTypes;

    /** 是否启用特色账单计费 */
    private Boolean billingEnabled;

    /** 卡片标识：特色账单或额外导出类型 */
    private Boolean hasSpecialExport;

    /** 导出 profile 标签，如「特色导出·分科室」 */
    private String exportProfileLabel;

    public ReconciliationJobResponse(Long id, String hospitalName, String sourceFileName,
                                     String sourceFilePath, Long sourceFileSize,
                                     Long ruleId, String ruleName, String ruleVersion,
                                     Integer versionNo, Integer totalRows, Integer correctedRows,
                                     Integer unchangedRows, Integer warningRows, Integer skippedRows,
                                     Double totalDifference, String reviewStatus, String reviewComment,
                                     String operatorName, String reviewerName, String sourceDateRange,
                                     LocalDateTime createdAt, LocalDateTime updatedAt,
                                     List<ReconciliationExportLogResponse> exports,
                                     List<Map<String, Object>> rows,
                                     List<String> sheetNames,
                                     Map<String, Integer> sheetRowCounts,
                                     Map<String, Integer> sheetWarningCounts,
                                     Integer logisticsTripCount,
                                     Double logisticsFee,
                                     Double originalTotalPrice,
                                     Double correctedTotalPrice) {
        this.id = id;
        this.hospitalName = hospitalName;
        this.sourceFileName = sourceFileName;
        this.sourceFilePath = sourceFilePath;
        this.sourceFileSize = sourceFileSize;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleVersion = ruleVersion;
        this.versionNo = versionNo;
        this.totalRows = totalRows;
        this.correctedRows = correctedRows;
        this.unchangedRows = unchangedRows;
        this.warningRows = warningRows;
        this.skippedRows = skippedRows;
        this.totalDifference = totalDifference;
        this.reviewStatus = reviewStatus;
        this.reviewComment = reviewComment;
        this.operatorName = operatorName;
        this.reviewerName = reviewerName;
        this.sourceDateRange = sourceDateRange;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.exports = exports;
        this.rows = rows;
        this.sheetNames = sheetNames;
        this.sheetRowCounts = sheetRowCounts;
        this.sheetWarningCounts = sheetWarningCounts;
        this.logisticsTripCount = logisticsTripCount;
        this.logisticsFee = logisticsFee;
        this.originalTotalPrice = originalTotalPrice;
        this.correctedTotalPrice = correctedTotalPrice;
    }
}
