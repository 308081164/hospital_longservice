package com.hospital.backend.dto.request.hospital;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 保存计费规则请求参数（DTO）
 *
 * 接收前端提交的完整计费规则配置，用于创建新规则或更新已有规则。
 * 前端会将定价规则编辑器中配置好的所有计费参数打包为 rules 对象提交。
 *
 * ── 数据流 ──
 * 前端（PricingRules 编辑器）→ SavePricingRuleRequest → Service 层 →
 * 将 rules Map 序列化为 JSON 字符串 → 存入 HospitalPricingRule.rulesJson 字段
 *
 * ── rules 字段结构 ──
 * rules 是一个深层嵌套的 Map&lt;String, Object&gt;，对应前端 TypeScript
 * 中的 PricingRules 接口，包含以下顶级模块：
 *
 * {
 *   "version": "1.0",                                    // 规则版本（冗余）
 *   "highTemperature": {                                  // 高温灭菌计费
 *     "nonWoven": {                                       //   无纺布包装
 *       "minCharge": 16.5,                                //     最低收费
 *       "flatPerPackagePrice": 5.5,                       //     超出阶梯后的单价
 *       "flatRateThreshold": 3                            //     阶梯阈值（包数）
 *     },
 *     "paperPlastic": {                                   //   纸塑袋包装
 *       "bagSizes": [...],                                //     各规格袋子配置
 *       "perPackagePrice": 5.5,                           //     每袋单价
 *       "minCharge": 16.5                                 //     最低收费
 *     }
 *   },
 *   "lowTemperature": { ... },                            // 低温灭菌计费
 *   "packaging": { ... },                                 // 包装耗材收费
 *   "needle": { ... },                                    // 小件/针类识别收费
 *   "cleaning": { ... },                                  // 清洗费用
 *   "logistics": { ... },                                 // 物流运输费用
 *   "settlementLetter": { ... },                          // 结款函模板配置
 *   "exportOptions": { ... }                              // 导出选项配置
 * }
 *
 * @see com.hospital.backend.entity.HospitalPricingRule 对应的实体类
 * @see com.hospital.backend.dto.response.hospital.PricingRuleResponse 响应 DTO
 */
@Data
public class SavePricingRuleRequest {

    /**
     * 规则名称（必填）
     *
     * 用于标识规则的用途，例如："标准灭菌计费规则"、"2024年Q1计费规则"。
     * 使用 @NotBlank 注解进行参数校验，前端传入时不能为空字符串。
     *
     * @see jakarta.validation.constraints.NotBlank
     */
    @NotBlank(message = "规则名称不能为空")
    private String name;

    /**
     * 规则版本号（必填）
     *
     * 用于追踪规则的迭代版本，例如："v1.0"、"v2.0"。
     * 版本号由前端传入，系统不会自动递增。
     * 使用 @NotBlank 注解进行参数校验。
     *
     * @see jakarta.validation.constraints.NotBlank
     */
    @NotBlank(message = "版本号不能为空")
    private String version;

    /**
     * 规则描述（可选）
     *
     * 用于补充说明规则的适用范围、生效日期、特殊条款等。
     * 非必填字段。
     */
    private String description;

    /**
     * 关联医院名称（可选）
     *
     * 为空表示全局规则，适用于所有医院。
     * 指定医院名称后，该规则仅用于该医院的账单核对。
     */
    private String hospitalName;

    private String planName;

    private String createdBy;

    /**
     * 完整定价规则配置对象（必填）
     *
     * 接收前端提交的完整定价规则 JSON 对象（Map 结构），
     * 包含高温灭菌、低温灭菌、包装收费、小件识别、清洗、物流、
     * 结款函、导出选项等全部计费维度的配置参数。
     *
     * Service 层会将此 Map 序列化为 JSON 字符串后存入数据库的 rulesJson 字段。
     */
    private Map<String, Object> rules;
}
