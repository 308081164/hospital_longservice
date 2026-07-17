package com.hospital.backend.config;

import com.hospital.backend.security.JwtAuthEntryPoint;
import com.hospital.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 安全配置
 *
 * 核心安全配置类，采用无状态（Stateless）JWT 认证模式，
 * 适合前后端分离的 RESTful API 架构。
 *
 * 安全策略要点：
 * - 禁用 CSRF（Cross-Site Request Forgery）：API 服务使用 Token 认证，
 *   不依赖 Session Cookie，天然免疫 CSRF 攻击
 * - 启用 CORS（Cross-Origin Resource Sharing）：允许前端跨域请求，
 *   开发环境允许所有来源，生产环境应限制为具体域名
 * - 无状态会话（SessionCreationPolicy.STATELESS）：不创建 HttpSession，
 *   每个请求独立认证，有利于水平扩展
 * - JWT 过滤器（JwtAuthenticationFilter）在 Spring Security 的
 *   UsernamePasswordAuthenticationFilter 之前执行，确保请求进入
 *   Controller 前已完成认证
 *
 * 公开路径列表（无需 JWT 认证即可访问）：
 * - POST /api/v1/base/access_token  - 用户登录
 * - POST /api/v1/base/refresh_token - 刷新令牌
 * - GET  /api/v1/base/health        - 健康检查
 * - GET  /api/v1/base/version       - 版本信息
 * - GET  /docs/** 等                - Swagger/OpenAPI 文档
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    /**
     * 安全过滤器链 - 定义所有安全规则和过滤器的执行顺序
     *
     * 配置了 CORS、CSRF、异常处理、会话管理、请求授权和 JWT 过滤器的集成。
     * 过滤器链的执行顺序为：
     * 1. CorsFilter（处理跨域）
     * 2. JwtAuthenticationFilter（JWT 认证）
     * 3. UsernamePasswordAuthenticationFilter（Spring Security 默认的认证过滤器）
     * 4. ... 其他 Spring Security 内部过滤器 ...
     * 5. ExceptionTranslationFilter（处理 AuthenticationException）
     * 6. FilterSecurityInterceptor（执行授权判断）
     *
     * @param http HttpSecurity 配置构建器
     * @return SecurityFilterChain 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS 配置 - 允许前端跨域请求
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 禁用 CSRF - API 服务使用 Token 认证，不需要 CSRF 保护
            .csrf(csrf -> csrf.disable())
            // 认证异常处理 - 返回 JSON 格式的 401 响应
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
            // 无状态会话 - 不创建 HttpSession，每次请求独立认证
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求授权配置
            .authorizeHttpRequests(auth -> auth
                // 公开路径：登录、刷新令牌、健康检查、版本信息、Swagger 文档
                .requestMatchers("/api/v1/base/access_token").permitAll()
                .requestMatchers("/api/v1/base/refresh_token").permitAll()
                .requestMatchers("/api/v1/base/health").permitAll()
                .requestMatchers("/api/v1/base/version").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                .requestMatchers("/docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 跨域配置
     *
     * 配置允许跨域访问的规则：
     * - allowedOriginPatterns("*")：允许所有来源（开发环境配置）
     * - allowedMethods：支持标准的 RESTful HTTP 方法 + OPTIONS（预检请求）
     * - allowedHeaders：允许前端携带的请求头（Content-Type、Authorization 等）
     * - allowCredentials(true)：允许携带认证凭据（如 Authorization 请求头）
     *
     * 生产环境中应将 allowedOriginPatterns 限制为具体的前端域名。
     *
     * @return CorsConfigurationSource CORS 配置源，注册到 /** 路径
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "*"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 密码编码器 - BCrypt 加密算法
     *
     * BCrypt 是强哈希函数，内置加盐机制，
     * 每次对同一明文加密结果不同，有效抵御彩虹表攻击。
     *
     * @return PasswordEncoder BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器 - 用于处理认证请求
     *
     * 从 AuthenticationConfiguration 中获取默认的 AuthenticationManager，
     * 由 Spring Security 自动装配 UserDetailsService 和 PasswordEncoder。
     *
     * @param config 认证配置
     * @return AuthenticationManager 认证管理器
     * @throws Exception 获取异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
