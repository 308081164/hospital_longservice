package com.hospital.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在所有响应上输出 X-App-Version 头（当前后端 gitSha，与 /api/v1/base/version 同源）。
 *
 * <p>前端 axios 响应拦截器逐个响应检查该头：运行期间后端重新部署导致 gitSha 变化时，
 * 立即触发强制升级（清缓存 + 阻断 UI + 硬刷新），确保旧 SPA 不可继续使用。
 *
 * <p>注册为最高优先级 servlet Filter，先于 Spring Security 链执行，
 * 因此 401/403 等安全异常响应同样携带版本头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppVersionHeaderFilter extends OncePerRequestFilter {

    public static final String VERSION_HEADER = "X-App-Version";

    @Value("${APP_GIT_SHA:local}")
    private String gitSha;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(VERSION_HEADER, normalize(gitSha));
        // 生产经 nginx 同源代理，此处主要为开发环境跨域直读该头兜底
        response.addHeader("Access-Control-Expose-Headers", VERSION_HEADER);
        filterChain.doFilter(request, response);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank() || "unknown".equalsIgnoreCase(raw.trim())) {
            return "local";
        }
        return raw.trim();
    }
}
