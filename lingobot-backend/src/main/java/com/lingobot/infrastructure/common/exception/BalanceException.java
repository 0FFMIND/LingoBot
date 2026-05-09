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

    private BalanceException(ErrorCode errorCode) {
        super(errorCode);
    }

    private BalanceException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static BalanceException of(ErrorCode errorCode) {
        return new BalanceException(errorCode);
    }

    public static BalanceException of(ErrorCode errorCode, String customMessage) {
        return new BalanceException(errorCode, customMessage);
    }

    public static BalanceException paymentRequired() {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED);
    }

    public static BalanceException paymentRequired(String customMessage) {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED, customMessage);
    }

    public static BalanceException insufficientBalance() {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED);
    }

    public static BalanceException insufficientBalance(String customMessage) {
        return new BalanceException(ErrorCode.PAYMENT_REQUIRED, customMessage);
    }
}
