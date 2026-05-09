package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 业务异常基类，所有业务异常都应该继承此类。
 * 包含错误码和自定义消息，便于统一异常处理。
 */
public abstract class BaseException extends RuntimeException {

    // 错误码，包含 code 和 message
    private final ErrorCode errorCode;

    // 使用 ErrorCode 的默认消息构造异常
    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 使用自定义消息覆盖 ErrorCode 默认消息
    protected BaseException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    // 返回绑定的错误码，供 GlobalExceptionHandler 提取 HTTP 状态码
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
