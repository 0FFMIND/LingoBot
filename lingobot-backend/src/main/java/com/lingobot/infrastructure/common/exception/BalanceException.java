package com.lingobot.infrastructure.common.exception;

import com.lingobot.infrastructure.common.response.ErrorCode;

/**
 * 余额相关异常，用于余额不足等场景。
 *
 *   throw BalanceException.insufficientBalance();
 *   throw BalanceException.insufficientBalance("当前余额: " + current + "，需要: " + cost);
 *   throw BalanceException.of(ErrorCode.PAYMENT_REQUIRED, "自定义消息");
 */
public class BalanceException extends BaseException {

    // 私有构造，仅由静态工厂方法调用（使用 ErrorCode 默认消息）
    private BalanceException(ErrorCode errorCode) {
        super(errorCode);
    }

    // 私有构造，仅由静态工厂方法调用（使用自定义消息）
    private BalanceException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    // 通用工厂：传入任意 ErrorCode，使用其默认消息
    public static BalanceException of(ErrorCode errorCode) {
        return new BalanceException(errorCode);
    }

    // 通用工厂：传入任意 ErrorCode，使用自定义消息
    public static BalanceException of(ErrorCode errorCode, String customMessage) {
        return new BalanceException(errorCode, customMessage);
    }

    // 需要付款 / 余额不足（通用）
    public static BalanceException paymentRequired() {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED);
    }

    // 需要付款 / 余额不足（自定义消息）
    public static BalanceException paymentRequired(String customMessage) {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED, customMessage);
    }

    // 余额不足（明确的余额扣减失败场景）
    public static BalanceException insufficientBalance() {
        return new BalanceException(ErrorCode.INSUFFICIENT_BALANCE);
    }

    // 余额不足（自定义消息，可附带当前余额与所需余额信息）
    public static BalanceException insufficientBalance(String customMessage) {
        return new BalanceException(ErrorCode.INSUFFICIENT_BALANCE, customMessage);
    }
}
