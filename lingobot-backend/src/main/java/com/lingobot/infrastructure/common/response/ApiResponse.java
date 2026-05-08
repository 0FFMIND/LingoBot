package com.lingobot.infrastructure.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一 API 响应包装类。
 *
 * 所有 Controller 返回值都应包在这里，DELETE 除外（204 无响应体）。
 * HTTP 状态码由 Controller 通过 ResponseEntity 设置，响应体的 code 进一步表示业务结果：
 *   code == 200  查询或更新成功，data 字段携带业务数据。
 *   code == 201  创建成功，data 字段携带新建资源。
 *   code == 4xx  业务或请求错误，message 说明具体原因。
 *   code == 5xx  服务器或上游服务异常。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    // 201 Created，创建资源成功时使用，message 使用默认"创建成功"
    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.CREATED.getCode())
                .message(ErrorCode.CREATED.getMessage())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 201 Created，创建资源成功时使用，自定义 message（如"兑换码创建成功"）
    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.CREATED.getCode())
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 200 OK，查询或更新资源成功时使用，message 使用默认"操作成功"
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 200 OK，查询或更新资源成功时使用，自定义 message（如"登录成功"）
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 注：DELETE 成功不包装 ApiResponse，直接返回 ResponseEntity.noContent().build()。
    // HTTP 204 No Content 规范禁止携带响应体。

    // 业务错误，message 使用 ErrorCode 中定义的默认文案
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 业务错误，自定义 message（如异常中携带的具体原因）
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 仅用于上游服务（如 OpenRouter）透传动态错误码的场景。
    // 业务错误请走 error(ErrorCode, String)，此方法只作为兜底，限制 5xx 防止滥用。
    public static <T> ApiResponse<T> errorUpstream(int code, String message) {
        if (code < 500 || code > 599) {
            throw new IllegalArgumentException("errorUpstream 仅允许 5xx 状态码，收到: " + code);
        }
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
