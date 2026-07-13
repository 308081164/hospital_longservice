package com.hospital.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一 API 响应封装
 *
 * 所有控制器方法统一返回此类型，确保前端收到的 JSON 结构一致。
 * 响应格式：{ code: 200, msg: "OK", data: {...} }
 *
 * code 遵循 HTTP 状态码规范：
 * - 200：成功
 * - 400：参数错误（请求参数校验失败或业务参数校验失败）
 * - 401：未认证（令牌缺失、无效或已过期）
 * - 403：无权限（当前用户没有访问该资源的权限）
 * - 404：资源不存在（请求的资源在数据库中不存在）
 * - 422：参数校验失败（@Valid 注解触发的 Bean Validation 校验失败）
 * - 500：服务器内部错误（未捕获的异常）
 *
 * @JsonInclude(NON_NULL) 确保 data 字段为 null 时不序列化，
 * 减少不必要的网络传输。
 *
 * @param <T> data 字段的类型泛型
 *
 * 使用示例：
 * Result.success(user)              → {"code":200,"msg":"OK","data":{...}}
 * Result.fail(400, "参数错误")       → {"code":400,"msg":"参数错误"}
 * Result.fail(404, "不存在", info)   → {"code":404,"msg":"不存在","data":{...}}
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码，200 表示成功，非 200 表示失败 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /**
     * 操作成功（带数据）
     *
     * @param data 返回的数据对象
     * @param <T>  数据类型
     * @return Result 实例，code=200, msg="OK"
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "OK", data);
    }

    /**
     * 操作成功（自定义消息 + 数据）
     *
     * @param msg  成功提示信息
     * @param data 返回的数据对象
     * @param <T>  数据类型
     * @return Result 实例，code=200
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 操作失败（无数据）
     *
     * @param code 错误状态码
     * @param msg  错误描述
     * @param <T>  数据类型
     * @return Result 实例，data=null
     */
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 操作失败（带数据）
     *
     * @param code 错误状态码
     * @param msg  错误描述
     * @param data 附加的错误数据
     * @param <T>  数据类型
     * @return Result 实例
     */
    public static <T> Result<T> fail(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }

    public static Result<Void> success() {
        return new Result<>(200, "OK", null);
    }
}
