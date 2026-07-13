package com.hospital.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证失败入口点（Authentication Entry Point）
 *
 * 实现 Spring Security 的 AuthenticationEntryPoint 接口，
 * 当未认证用户（未提供有效 JWT 令牌）访问受保护的 API 端点时，
 * 此组件负责返回统一的 JSON 格式 401 错误响应。
 *
 * 为什么需要自定义 AuthenticationEntryPoint：
 * - Spring Security 默认行为是将未认证请求重定向到登录页面（适用于表单登录）
 * - 在前后端分离的 REST API 架构中，我们需要返回 JSON 格式的错误响应
 * - 前端通过检测 401 状态码统一跳转到登录页面或刷新令牌
 *
 * 返回的 JSON 格式与 Result 统一响应结构一致：
 * {"code":401,"msg":"Unauthorized","data":null}
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    /**
     * 认证失败时的处理逻辑
     *
     * 直接返回 JSON 格式的 401 响应，格式与 Result<?> 一致。
     * 响应示例：{"code":401,"msg":"Unauthorized","data":null}
     *
     * @param request       导致认证失败的 HTTP 请求
     * @param response      HTTP 响应
     * @param authException 认证异常信息
     * @throws IOException 写入响应时可能抛出的 IO 异常
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized\",\"data\":null}");
    }
}
