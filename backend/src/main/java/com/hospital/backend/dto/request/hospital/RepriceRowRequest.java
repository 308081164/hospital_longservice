package com.hospital.backend.dto.request.hospital;

import lombok.Getter;
import lombok.Setter;

/**
 * 单行保存并重算请求。
 *
 * 所有字段均可选：null 表示不修改该字段，仅触发重算；
 * 非 null 字段会先覆盖到行数据，再用 PricingEngine 重算该行。
 */
@Getter
@Setter
public class RepriceRowRequest {

    /** 类型（如：敷料包 / 器械包） */
    private String type;

    /** 包装材料（如：高温纸塑袋200*440） */
    private String packageMaterial;

    /** 器械数量 */
    private Integer instrumentCount;

    /** 打包数量（包数） */
    private Integer packCount;
}
