package com.hospital.backend.dto.response.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 模板引用响应 DTO
 *
 * 返回给前端的模板引用信息，用于展示可供选择的 Excel 模板列表。
 * 包括结款函模板和账单模板两种类型。
 *
 * ── 业务用途 ──
 * 前端导出结款函或账单时，需要先请求可用的模板列表，
 * 然后用户选择其中一个模板进行导出。
 * 此 DTO 即为模板列表中的每一项。
 *
 * ── 对应前端类型 ──
 * 对应前端 TypeScript 中的 BackendTemplateRef 接口。
 */
@Data
@AllArgsConstructor
public class TemplateRefResponse {

    /**
     * 模板 ID
     *
     * 唯一标识一个模板。由后端在配置中定义。
     * 前端选择模板后，将此 ID 回传给后端用于定位具体模板。
     */
    private String id;

    /**
     * 模板名称
     *
     * 给用户展示的模板名称，例如：
     *   - "标准结款函模板"
     *   - "简化版账单模板"
     */
    private String name;

    /**
     * 模板描述
     *
     * 模板的简要说明，描述模板的格式特点或适用场景，例如：
     *   - "包含医院名称、费用明细、合计金额的标准格式"
     *   - "不含附件明细的简化版账单"
     */
    private String description;
}
