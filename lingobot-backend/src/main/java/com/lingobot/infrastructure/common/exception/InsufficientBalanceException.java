package com.lingobot.infrastructure.common.exception;

// 余额不足异常：当用户余额不足以支付本次 API 调用时抛出，由 GlobalExceptionHandler 捕获并返回 402
public class InsufficientBalanceException extends RuntimeException {
    
    private final double currentBalance;
    private final double requiredCost;
    
    public InsufficientBalanceException(String message) {
        super(message);
        this.currentBalance = 0.0;
        this.requiredCost = 0.0;
    }
    
    public InsufficientBalanceException(double currentBalance, double requiredCost) {
        super("余额不足。当前余额: " + currentBalance + "，需要: " + requiredCost);
        this.currentBalance = currentBalance;
        this.requiredCost = requiredCost;
    }
    
    public InsufficientBalanceException(String message, double currentBalance, double requiredCost) {
        super(message);
        this.currentBalance = currentBalance;
        this.requiredCost = requiredCost;
    }
    
    public double getCurrentBalance() {
        return currentBalance;
    }
    
    public double getRequiredCost() {
        return requiredCost;
    }
}
