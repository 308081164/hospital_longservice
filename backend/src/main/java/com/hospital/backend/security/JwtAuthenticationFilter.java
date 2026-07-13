package com.hospital.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * 继承 OncePerRequestFilter，Spring 保证每个请求仅执行一次 doFilterInternal。
 * 核心职责：从 HTTP 请求头中提取 Bearer Token，解析验证后
 * 将认证信息（Authentication）注入到 SecurityContextHolder 中。
 *
 * 工作流程：
 * 1. 从 Authorization 请求头中提取 Bearer Token 字符串
 * 2. 使用 JwtTokenProvider 验证令牌的签名和有效期
 * 3. 从令牌中解析用户名，加载 UserDetails
 * 4. 构建 UsernamePasswordAuthenticationToken 并设置到 SecurityContext
 * 5. 放行请求到过滤器链的下一个节点
 *
 * 过滤链位置：在 Spring Security 的 UsernamePasswordAuthenticationFilter
 * 之前执行（通过 SecurityConfig 中 addFilterBefore 配置），
 * 确保请求在到达 Controller 之前已完成 JWT 认证。
 *
 * 如果令牌缺失或无效，过滤器不会抛出异常，而是继续执行过滤器链，
 * 未认证的请求最终由 SecurityConfig 中的 .anyRequest().authenticated()
 * 规则拦截并返回 401 响应。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * 每次请求的核心过滤逻辑（每个请求执行一次）
     *
     * 认证流程：
     * 1. extractToken() 从 Authorization 请求头提取 Bearer Token
     * 2. JwtTokenProvider.validateToken() 验证签名和过期时间
     * 3. 从令牌解析用户名，调用 UserDetailsService 加载用户详情
     * 4. 构建 UsernamePasswordAuthenticationToken（含权限信息）
     * 5. 设置 WebAuthenticationDetails（客户端 IP、Session ID 等）
     * 6. 将 Authentication 注入 SecurityContextHolder
     * 7. 调用 filterChain.doFilter() 放行请求至下一个过滤器
     *
     * 注意：如果令牌验证失败或不存在，SecurityContext 不会被设置，
     * 请求会以匿名用户身份继续处理，最终被 SecurityConfig 拦截返回 401。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String tokenType = jwtTokenProvider.getTokenTypeFromToken(token);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = jwtTokenProvider.getUsernameFromToken(token);
            UserDetailsImpl userDetails;
            try {
                userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException e) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求头中提取 Bearer Token
     *
     * 期望格式：Authorization: Bearer <token>
     * 无令牌或格式错误时返回 null（未登录请求会由 JwtAuthEntryPoint 处理）
     *
     * @param request HTTP 请求
     * @return JWT 令牌字符串，或 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
