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

    // 私有构造，仅由静态工厂方法调用（使用 ErrorCode 默认消息）
    private BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    // 私有构造，仅由静态工厂方法调用（使用自定义消息）
    private BusinessException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    // 通用工厂：传入任意 ErrorCode，使用其默认消息
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    // 通用工厂：传入任意 ErrorCode，使用自定义消息
    public static BusinessException of(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, customMessage);
    }

    // 请求参数错误
    public static BusinessException badRequest() {
        return new BusinessException(ErrorCode.BAD_REQUEST);
    }

    // 请求参数错误（自定义消息）
    public static BusinessException badRequest(String customMessage) {
        return new BusinessException(ErrorCode.BAD_REQUEST, customMessage);
    }

    // 资源不存在
    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    // 资源不存在（自定义消息）
    public static BusinessException notFound(String customMessage) {
        return new BusinessException(ErrorCode.NOT_FOUND, customMessage);
    }

    // 请求过于频繁
    public static BusinessException tooManyRequests() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
    }

    // 请求过于频繁（自定义消息）
    public static BusinessException tooManyRequests(String customMessage) {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, customMessage);
    }
}
