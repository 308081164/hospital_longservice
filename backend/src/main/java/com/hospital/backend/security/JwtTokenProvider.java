package com.hospital.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 令牌工具类
 *
 * 负责 JWT（JSON Web Token）的创建、解析和验证。
 * 使用 HMAC-SHA（基于 Base64 解码后的密钥）签名算法，
 * 支持两种类型的令牌：
 *
 * 1. Access Token（访问令牌）：
 *    - 有效期短（默认 240 分钟，由配置项 access-token-expire-minutes 控制）
 *    - 用于 API 请求的身份认证，放在 Authorization 请求头中
 *    - token_type = "access"
 *
 * 2. Refresh Token（刷新令牌）：
 *    - 有效期长（默认 7 天，由配置项 refresh-token-expire-days 控制）
 *    - 用于在 Access Token 过期后获取新的令牌对
 *    - token_type = "refresh"
 *
 * 令牌载荷自定义声明：
 * - user_id：用户唯一标识（Long）
 * - username：登录用户名（String）
 * - is_superuser：是否为超级管理员（Boolean）
 * - token_type：令牌类型（String："access" / "refresh"）
 * - iat：签发时间（Date）
 * - exp：过期时间（Date）
 *
 * 使用 io.jsonwebtoken 库（JJWT）实现，版本 >= 0.12.x。
 */
@Component
public class JwtTokenProvider {

    /** HMAC-SHA 签名密钥（由 application.yml 中的 secret 解码生成） */
    private final SecretKey secretKey;

    /** Access Token 过期时间（分钟） */
    private final long accessTokenExpireMinutes;

    /** Refresh Token 过期时间（天） */
    private final long refreshTokenExpireDays;

    /**
     * 构造方法 - 初始化密钥和过期时间配置
     *
     * @param secret                     Base64 编码的密钥字符串
     * @param accessTokenExpireMinutes   Access Token 过期分钟数
     * @param refreshTokenExpireDays     Refresh Token 过期天数
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expire-minutes}") long accessTokenExpireMinutes,
            @Value("${app.jwt.refresh-token-expire-days}") long refreshTokenExpireDays) {
        byte[] keyBytes = Decoders.BASE64URL.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpireMinutes = accessTokenExpireMinutes;
        this.refreshTokenExpireDays = refreshTokenExpireDays;
    }

    /**
     * 创建 Access Token（短期令牌）
     *
     * 用于 API 请求的身份验证，有效期较短（默认 240 分钟）。
     * token_type = "access" 用于区分令牌类型。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param isSuperuser 是否为超级管理员
     * @return JWT 令牌字符串
     */
    public String createAccessToken(Long userId, String username, Boolean isSuperuser) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpireMinutes * 60 * 1000);

        return Jwts.builder()
                .claim("user_id", userId)
                .claim("username", username)
                .claim("is_superuser", isSuperuser)
                .claim("token_type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 创建 Refresh Token（长期令牌）
     *
     * 用于在 Access Token 过期后获取新的令牌对，有效期较长（默认 7 天）。
     * token_type = "refresh" 用于区分令牌类型。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param isSuperuser 是否为超级管理员
     * @return JWT 令牌字符串
     */
    public String createRefreshToken(Long userId, String username, Boolean isSuperuser) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpireDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .claim("user_id", userId)
                .claim("username", username)
                .claim("is_superuser", isSuperuser)
                .claim("token_type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 JWT 令牌，获取载荷声明
     *
     * 验证签名并返回令牌中的所有声明（claims）。
     *
     * @param token JWT 令牌字符串
     * @return Claims 包含所有声明的载荷对象
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中提取用户 ID
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("user_id", Long.class);
    }

    /**
     * 从令牌中提取用户名
     *
     * @param token JWT 令牌字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从令牌中提取令牌类型
     *
     * @param token JWT 令牌字符串
     * @return 令牌类型字符串
     */
    public String getTokenTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("token_type", String.class);
    }

    /**
     * 验证令牌是否有效
     *
     * 尝试解析签名和过期时间，若解析成功返回 true，
     * 任何 JWT 异常（签名错误、过期、格式非法）均返回 false。
     *
     * @param token JWT 令牌字符串
     * @return true 表示令牌有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
