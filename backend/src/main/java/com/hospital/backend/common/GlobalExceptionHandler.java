package com.hospital.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 使用 @RestControllerAdvice 拦截所有控制器抛出的异常，
 * 统一转换为 Result<?> 格式的 JSON 响应，避免直接将异常堆栈暴露给前端。
 *
 * 覆盖常见异常类型：
 * - 参数校验异常（@Valid 校验失败）
 * - 认证异常（未登录/令牌失效）
 * - 权限异常（无权访问）
 * - 参数非法异常
 * - 通用未知异常（兜底）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 @Valid / @Validated 参数校验失败异常
     *
     * 当控制器方法中使用 @Valid 注解标记的参数校验不通过时触发。
     * 例如 LoginRequest 中 @NotBlank 注解的字段为空，
     * 或 UserCreateRequest 中 @Email 注解的邮箱格式不正确等。
     *
     * 处理逻辑：遍历所有校验失败的字段，提取每个字段注解中
     * message 属性的错误描述，用逗号拼接后返回给前端。
     *
     * 返回值使用 422（Unprocessable Entity）状态码，区别于 400（Bad Request）。
     *
     * @param ex 方法参数校验失败异常，包含所有字段的错误信息
     * @return Result 422 状态码 + 所有校验错误信息（逗号分隔）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException ex) {
        // 遍历所有校验失败的字段，收集每个字段的 @NotBlank/@Size/@Email 等注解中的 message
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.fail(422, msg);
    }

    /**
     * 处理 Spring Security 认证异常（未登录或令牌无效）
     *
     * 当客户端未提供有效 JWT 令牌或令牌已过期时触发。
     * 注意：JwtAuthenticationFilter 中验证失败的请求会走到这里，
     * 但 JwtAuthEntryPoint 已经拦截了未认证的请求并返回了 401。
     * 此处理器主要处理在控制器方法执行过程中发生的认证异常。
     *
     * @param ex Spring Security 认证异常
     * @return Result 401 状态码 + "认证失败"
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<?> handleAuth(AuthenticationException ex) {
        return Result.fail(401, "认证失败");
    }

    /**
     * 处理访问被拒绝异常（无权限）
     *
     * @param ex Spring Security 访问拒绝异常
     * @return Result 403 状态码 + "无权限访问"
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDenied(AccessDeniedException ex) {
        return Result.fail(403, "无权限访问");
    }

    /**
     * 处理非法参数异常
     *
     * 当业务逻辑校验发现参数不合法时抛出此异常，
     * 直接返回异常中的错误消息。
     *
     * @param ex 非法参数异常
     * @return Result 400 状态码 + 异常消息
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArg(IllegalArgumentException ex) {
        return Result.fail(400, ex.getMessage());
    }

    /**
     * 处理所有未捕获的未知异常（兜底处理器）
     *
     * 记录完整的错误日志以便排查问题，
     * 但返回给前端的是通用错误消息，避免泄露敏感信息。
     *
     * @param ex 未知异常
     * @return Result 500 状态码 + "服务器内部错误"
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleGeneral(Exception ex) {
        log.error("Unexpected error:", ex);
        return Result.fail(500, "服务器内部错误");
    }
}
