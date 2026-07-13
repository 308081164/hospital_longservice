package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建导出日志请求参数（DTO）
 *
 * 当用户从核对任务导出结款函、账单等文件到本地时，
 * 系统会创建一条导出日志记录本次操作。该 DTO 接收前端提交的
 * 导出类型、文件名和操作人员信息。
 *
 * ── 数据流 ──
 * 前端用户点击导出 → 生成导出文件 →
 * 提交 CreateExportLogRequest → Service 层创建导出日志 →
 * 写入 HospitalReconciliationExportLog 表
 *
 * ── 导出类型说明 ──
 * bill              → Excel 账单（按行展示所有核对明细）
 * settlement        → Excel 结款函（按费用类别汇总）
 * html_settlement   → HTML 格式结款函（在线查看）
 * print_bill        → 打印版账单（HTML 格式，优化打印样式）
 * print_settlement  → 打印版结款函（HTML 格式，优化打印样式）
 *
 * @see com.hospital.backend.entity.HospitalReconciliationExportLog 对应的实体类
 * @see com.hospital.backend.dto.response.hospital.ReconciliationExportLogResponse 响应 DTO
 */
@Data
public class CreateExportLogRequest {

    /**
     * 导出类型（必填）
     *
     * 标识本次导出的文件类型和格式，取值范围：
     *   bill              → Excel 格式账单明细
     *   settlement        → Excel 格式结款函
     *   html_settlement   → HTML 格式结款函（在线查看）
     *   print_bill        → 打印版账单（HTML 格式，优化打印样式）
     *   print_settlement  → 打印版结款函（HTML 格式，优化打印样式）
     *
     * 使用 @NotBlank 注解进行参数校验。
     * 与前端的 json 互转时使用下划线命名 "export_type"。
     */
    @NotBlank(message = "导出类型不能为空")
    private String exportType;

    /**
     * 导出的文件名（可选）
     *
     * 导出文件的展示名称，例如："某某医院2024年1月账单.xlsx"。
     * 用于前端展示下载文件名称。
     * 非必填字段。
     *
     * 与前端的 json 互转时使用下划线命名 "file_name"。
     */
    private String fileName;

    /**
     * 操作人员名称（必填）
     *
     * 执行导出操作的用户真实姓名，用于操作日志和审计追踪。
     * 注意：此为用户真实姓名（如"赵六"），而非登录用户名。
     *
     * 使用 @NotBlank 注解进行参数校验。
     */
    @NotBlank(message = "操作人不能为空")
    private String operatorName;
}
