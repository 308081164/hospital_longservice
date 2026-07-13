package com.hospital.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建用户请求参数
 *
 * 用于创建新用户，包含基本信息和角色分配。
 * 用户名长度限制 3-20 位，密码至少 8 位，
 * 邮箱需符合标准格式。
 */
@Data
public class UserCreateRequest {

    /**
     * 用户名（3-20 位字符，必填）
     * 登录凭证之一，系统中必须唯一，创建时会自动检查重名。
     * 支持字母、数字、下划线组合。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度3-20位")
    private String username;

    /**
     * 邮箱地址（必填，需符合标准邮箱格式）
     * 用于接收系统通知和密码找回，系统中必须唯一。
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 登录密码（至少 8 位，必填）
     * 服务端会使用 BCrypt 算法加密存储，原始密码不会明文保存。
     * 要求包含字母和数字组合以增强安全性。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度至少8位")
    private String password;

    /**
     * 是否启用该用户（默认启用）
     * 被禁用的用户无法登录系统，但保留其数据不删除。
     */
    private Boolean isActive = true;

    /**
     * 是否为超级管理员（默认否）
     * 超级管理员拥有所有菜单和 API 的访问权限，不受 RBAC 角色权限约束。
     */
    private Boolean isSuperuser = false;

    /**
     * 初始角色 ID 列表（可选）
     * 创建用户时直接分配角色，角色须已存在于数据库中。
     * 如不传此字段，创建的用户将没有任何权限。
     */
    private List<Long> roleIds;
}
