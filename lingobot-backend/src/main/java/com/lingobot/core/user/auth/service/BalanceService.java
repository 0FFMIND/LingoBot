package com.lingobot.core.user.auth.service;

/**
 * 余额服务接口
 * 用于管理用户余额的查询、扣费和充值
 */
public interface BalanceService {
    
    /**
     * 获取当前用户的余额
     * @return 当前用户的余额
     */
    int getCurrentUserBalance();
    
    /**
     * 检查用户余额是否足够
     * @param cost 需要消耗的点数
     * @return 如果余额足够返回true，否则返回false
     */
    boolean hasSufficientBalance(double cost);
    
    /**
     * 扣除用户余额
     * @param cost 需要消耗的点数
     * @return 扣除后的余额
     * @throws InsufficientBalanceException 如果余额不足
     */
    int deductBalance(double cost);
    
    /**
     * 为用户充值
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 充值后的余额
     */
    int addBalance(Long userId, int amount);
    
    /**
     * 为当前用户充值
     * @param amount 充值金额
     * @return 充值后的余额
     */
    int addCurrentUserBalance(int amount);
}
