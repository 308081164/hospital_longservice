package com.hospital.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前用户信息响应
 *
 * 登录后获取当前用户的基本信息和角色列表。
 * 字段命名混合了两种风格：
 * - snake_case（is_active、is_superuser、last_login）：通过 @JsonProperty 映射
 * - camelCase（createdAt、updatedAt、username）：使用默认序列化
 *
 * 这种策略是为了匹配前端 Vue 项目的类型定义规范。
 */
@Data
@AllArgsConstructor
public class UserInfoResponse {

    /** 用户唯一标识 ID */
    private Long id;

    /** 登录用户名（系统中唯一） */
    private String username;

    /** 用户电子邮箱地址 */
    private String email;

    /** 用户头像图片的 URL 地址 */
    private String avatar;

    /**
     * 用户是否启用
     * false 表示用户被禁用，无法登录系统。
     */
    @JsonProperty("is_active")
    private Boolean isActive;

    /**
     * 是否为超级管理员
     * 超级管理员拥有系统全部权限，不受 RBAC 角色权限约束。
     */
    @JsonProperty("is_superuser")
    private Boolean isSuperuser;

    /**
     * 用户拥有的角色名称列表
     * 如 ["管理员"]、["财务专员", "审计员"]。
     * 前端据此控制页面上的功能按钮和操作权限。
     */
    private List<String> roles;

    /** 用户账号的创建时间 */
    private LocalDateTime createdAt;

    /** 用户信息的最后更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 用户的最后登录时间
     * 每次成功登录时由 AuthController 更新此字段。
     * 可用于检测账号的活跃度。
     */
    @JsonProperty("last_login")
    private LocalDateTime lastLogin;
}
