package com.hospital.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功响应
 *
 * 登录成功后返回的令牌信息。
 * 使用 @JsonProperty 将字段映射为前端期望的 snake_case 命名：
 * - access_token：JWT 访问令牌
 * - refresh_token：JWT 刷新令牌
 * - token_type：令牌类型（固定为 "bearer"）
 * - expires_in：令牌过期剩余秒数
 *
 * 前端在后续请求中将 access_token 放入 Authorization 头：
 * Authorization: Bearer <access_token>
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT 访问令牌（Access Token）
     * 用于 API 请求的身份认证，需放在请求头的 Authorization 字段中。
     * 格式：Authorization: Bearer <access_token>
     * 有效期较短（默认 240 分钟），过期后需使用 refresh_token 刷新。
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * JWT 刷新令牌（Refresh Token）
     * 用于在 Access Token 过期后获取新的令牌对。
     * 有效期较长（默认 7 天），过期后用户需重新登录。
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** 登录用户的用户名 */
    private String username;

    /**
     * 令牌类型（固定值 "bearer"）
     * 告知前端在 Authorization 请求头中使用的认证方案。
     */
    @JsonProperty("token_type")
    private String tokenType = "bearer";

    /**
     * 访问令牌过期剩余秒数
     * 前端可据此提前刷新令牌，避免请求因令牌过期而失败。
     * 计算公式：accessTokenExpireMinutes * 60
     */
    @JsonProperty("expires_in")
    private long expiresIn;
}
