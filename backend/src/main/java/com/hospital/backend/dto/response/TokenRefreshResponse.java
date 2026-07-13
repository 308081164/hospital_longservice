package com.hospital.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 令牌刷新响应
 *
 * 当 Access Token 过期时，使用 Refresh Token 换取新的令牌对。
 * 响应结构与 LoginResponse 一致，前端可以复用相同的处理逻辑。
 */
@Data
@AllArgsConstructor
public class TokenRefreshResponse {

    /**
     * 新的 JWT 访问令牌（Access Token）
     * 替换旧的已过期访问令牌，用于后续 API 请求的认证。
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * 新的 JWT 刷新令牌（Refresh Token）
     * 每次刷新时同时刷新 Refresh Token，实现令牌的滑动过期机制。
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 令牌类型（固定值 "bearer"）
     * 与前端的认证方案保持一致。
     */
    @JsonProperty("token_type")
    private String tokenType = "bearer";

    /**
     * 新的访问令牌过期剩余秒数
     * 供前端计算下一次刷新时机。
     */
    @JsonProperty("expires_in")
    private long expiresIn;
}
