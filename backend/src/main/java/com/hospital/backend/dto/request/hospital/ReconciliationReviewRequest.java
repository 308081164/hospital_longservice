package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 核对任务审核请求参数（DTO）
 *
 * 用于审核人员对核对任务进行审批或驳回操作。
 * 系统会在审核通过后更新 HospitalReconciliationJob 的 reviewStatus 字段。
 *
 * ── 审核流程 ──
 * 1. 核对完成 → reviewStatus = "pending"（前端展���待审核标签）
 * 2. 审核人员查看核对详情（总行数、已更正行数、总差价等）
 * 3. 审核人员提交审核意见 → 更新 reviewStatus
 *    - approved → 可进行导出操作
 *    - rejected → 需要修改规则后重新核对
 *
 * ── 数据流 ──
 * 前端 → ReconciliationReviewRequest → Service 层验证审核状态合法性 →
 * 更新 HospitalReconciliationJob 的 reviewStatus、reviewComment、reviewerName
 *
 * @see com.hospital.backend.entity.HospitalReconciliationJob 对应的实体
 */
@Data
public class ReconciliationReviewRequest {

    /**
     * 审核状态（必填）
     *
     * 取值范围：
     *   pending  → 保持待审核状态（一般不使用此值提交审核）
     *   approved → 审核通过，确认核对结果无误，可以进行导出操作
     *   rejected → 审核驳回，核对结果有问题，需要重新核对
     *
     * 注意：此字段与 HospitalReconciliationJob.reviewStatus 保持一致。
     * 使用 @NotBlank 注解进行参数校验。
     */
    @NotBlank(message = "审核状态不能为空")
    private String reviewStatus;

    /**
     * 审核意见（可选）
     *
     * 审核人员填写的审核说明或驳回原因。
     * 例如：
     *   通过时："核对结果无误，同意导出结款函。"
     *   驳回时："第15-20行数据存在异常，请重新核对后再提交审核。"
     */
    private String reviewComment;

    /**
     * 审核人员名称（必填）
     *
     * 执行审核操作的用户真实姓名（如"王五"）。
     * 用于追溯是谁审核了本次核对任务。
     * 使用 @NotBlank 注解进行参数校验。
     */
    @NotBlank(message = "审核人不能为空")
    private String reviewerName;
}
