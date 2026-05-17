package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * 全局异常拦截器。
 *
 * @RestControllerAdvice 会拦截所有 Controller 抛出的异常，
 * 在异常到达客户端之前统一包装成 ApiResponse 格式返回，
 * 避免 Spring 默认的 /error 白页或裸异常堆栈暴露给前端。
 *
 * 每个 @ExceptionHandler 方法负责一种异常类型：
 * 设置合适的 HTTP 状态码 + 将错误信息填入 ApiResponse.error()，
 * 前端同时拿到 HTTP 状态码和响应体中的 code / message。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // SSE 长连接正常断开时触发，无需返回响应体
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        log.debug("SSE 连接超时: {}", ex.getMessage());
    }

    // 聊天相关业务异常（对话、消息、AI 响应等）→ 返回 HTTP + ErrorCode + 异常消息
    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatException(ChatException ex) {
        log.error("聊天业务异常: {}", ex.getMessage());
        return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // 认证与用户相关异常（注册、登录、账户管理等）→ 返回 HTTP + ErrorCode + 异常消息
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        log.warn("认证异常: {}", ex.getMessage());
        return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // 通用业务异常（无明确分类的业务错误）→ 返回 HTTP + ErrorCode + 异常消息
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("业务异常: {}", ex.getMessage());
        return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // 限流触发时抛出（如登录频率过高）→ 429
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("业务状态异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ErrorCode.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    // 请求参数校验失败 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("参数异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    // JWT 无效或未登录，Spring Security 抛出 → 401
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED));
    }

    // 已登录但权限不足（如普通用户访问管理员接口）→ 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("访问被拒绝: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN));
    }

    // 用户余额相关异常（如余额不足等）→ 返回 HTTP + ErrorCode.PAYMENT_REQUIRED + 异常消息
    @ExceptionHandler(BalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBalanceException(BalanceException ex) {
        log.warn("余额异常: {}", ex.getMessage());
        return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // 媒体相关异常（TTS、音频处理等）→ 返回 HTTP + ErrorCode + 异常消息
    @ExceptionHandler(MediaException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaException(MediaException ex) {
        log.error("媒体异常: {}", ex.getMessage());
        return ResponseEntity.status(extractHttpStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // 未被上面规则覆盖的 RuntimeException 兜底 → 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    // 所有其他未知异常兜底 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("未知异常: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    // 根据 ErrorCode.code 推导 HTTP 状态码：4xx/5xx 直接用，1xxx 业务码→400，其余→500
    private HttpStatus extractHttpStatus(ErrorCode errorCode) {
        int code = errorCode.getCode();
        if (code >= 400 && code <= 599) {
            return HttpStatus.valueOf(code);
        }
        if (code >= 1000) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
