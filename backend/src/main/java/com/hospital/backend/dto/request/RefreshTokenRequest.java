package com.hospital.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求参数 DTO
 *
 * 当 Access Token 过期时（前端通过 expires_in 判断），
 * 使用 Refresh Token 换取新的令牌对（Access Token + Refresh Token）。
 *
 * Refresh Token 的原理：
 * - 有效期比 Access Token 长（默认 7 天 vs 240 分钟）
 * - 用于无感刷新令牌，避免频繁要求用户重新登录
 * - 每次刷新时同时刷新 Refresh Token，实现滑动过期机制
 *
 * 请求示例：
 * {
 *   "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
 * }
 */
@Data
public class RefreshTokenRequest {

    /**
     * 刷新令牌字符串（不能为空）
     * 由 JwtTokenProvider.createRefreshToken() 生成，
     * 包含 token_type=refresh 声明，用于与 Access Token 做区分。
     */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
