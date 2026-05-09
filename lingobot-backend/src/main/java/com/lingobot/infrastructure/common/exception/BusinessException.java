package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 通用业务异常，用于没有明确分类的业务错误（如参数校验失败、资源不存在等）。
 *
 *   throw BusinessException.badRequest("参数错误");
 *   throw BusinessException.notFound("资源不存在");
 *   throw BusinessException.of(ErrorCode.SOME_CODE, "自定义消息");
 */
public class BusinessException extends BaseException {

    private BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    private BusinessException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, customMessage);
    }

    public static BusinessException badRequest() {
        return new BusinessException(ErrorCode.BAD_REQUEST);
    }

    public static BusinessException badRequest(String customMessage) {
        return new BusinessException(ErrorCode.BAD_REQUEST, customMessage);
    }

    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public static BusinessException notFound(String customMessage) {
        return new BusinessException(ErrorCode.NOT_FOUND, customMessage);
    }

    public static BusinessException tooManyRequests() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
    }

    public static BusinessException tooManyRequests(String customMessage) {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, customMessage);
    }
}
