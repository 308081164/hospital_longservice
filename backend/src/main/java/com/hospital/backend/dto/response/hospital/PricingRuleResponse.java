package com.hospital.backend.dto.response.hospital;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 计费规则响应 DTO
 *
 * 返回给前端的计费规则完整信息。Service 层将实体中的 rulesJson（String）
 * 反序列化为 rules（Map）后封装到此 DTO 中返回。
 *
 * ── 数据流 ──
 * 数据库查询 → HospitalPricingRule 实体 →
 * JsonUtils.parseToMap(rulesJson) → rules Map →
 * 封装到 PricingRuleResponse → 返回前端
 *
 * ── 对应前端类型 ──
 * 对应前端 TypeScript 中的 PricingRule 接口。
 *
 * @see com.hospital.backend.entity.HospitalPricingRule 对应的实体类
 * @see com.hospital.backend.common.JsonUtils           JSON 反序列化工具
 */
@Data
public class PricingRuleResponse {

    /**
     * 规则 ID（主键）
     */
    private Long id;

    /**
     * 规则名称
     *
     * 例如："标准灭菌计费规则"、"2024年Q1计费规则"
     */
    private String name;

    /**
     * 规则版本号
     *
     * 例如："v1.0"、"v2.0"
     */
    private String version;

    /**
     * 规则描述
     *
     * 说明规则的适用范围、特殊说明等
     */
    private String description;

    /**
     * 是否当前激活的规则
     *
     * true  → 当前生效的规则
     * false → 历史规则
     */
    private Boolean isActive;

    /**
     * 关联医院名称（可选）
     *
     * 为空表示全局规则，所有医院通用。
     * 有值时仅用于该医院的账单核对。
     */
    private String hospitalName;

    private String planName;

    /**
     * 完整定价规则配置（Map 结构）
     *
     * 从实体字段 rulesJson（String）反序列化得到的 Map 对象。
     * 包含高温灭菌、低温灭菌、包装收费、小件识别、清洗、物流、
     * 结款函、导出选项等全部计费维度的配置。
     *
     * 前端直接使用此 Map 渲染定价规则编辑器。
     */
    private Map<String, Object> rules;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 全参构造函数
     *
     * @param id          规则 ID
     * @param name        规则名称
     * @param version     规则版本号
     * @param description 规则描述
     * @param isActive    是否激活
     * @param hospitalName 关联医院名称
     * @param rules       反序列化后的定价规则 Map
     * @param createdAt   创建时间
     * @param updatedAt   更新时间
     */
    public PricingRuleResponse(Long id, String name, String version, String description,
                               Boolean isActive, String hospitalName, String planName,
                               Map<String, Object> rules,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.isActive = isActive;
        this.hospitalName = hospitalName;
        this.planName = planName;
        this.rules = rules;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
