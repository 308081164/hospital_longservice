package com.hospital.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数 DTO
 *
 * 封装用户登录时提交的用户名和密码。
 * 两个字段均使用 @NotBlank 注解进行空值校验，确保认证前参数完整性。
 * 具体的认证逻辑在 AuthController.login() 中处理，
 * 包括用户查找、BCrypt 密码比对、用户状态检查以及 JWT 令牌生成。
 *
 * 请求示例：
 * {
 *   "username": "admin",
 *   "password": "abcd1234"
 * }
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名（不能为空）
     * 对应数据库中 sys_user 表的 username 字段，系统中必须唯一。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码（不能为空）
     * 服务端使用 BCrypt 算法与数据库中的加密密码进行比对验证。
     * 密码明文不会在日志或响应中泄露。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
