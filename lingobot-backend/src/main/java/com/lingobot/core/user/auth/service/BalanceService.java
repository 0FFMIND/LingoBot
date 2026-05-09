package com.lingobot.core.user.auth.service;

import com.lingobot.core.user.auth.dto.BalanceTransactionDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 余额服务接口
 * 用于管理用户余额的查询、扣费和充值
 */
public interface BalanceService {
    
    /**
     * 获取当前用户的余额
     * @return 当前用户的余额
     */
    double getCurrentUserBalance();
    
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
     * @throws BalanceException 如果余额不足
     */
    double deductBalance(double cost);
    
    /**
     * 扣除用户余额并记录交易
     * @param cost 需要消耗的点数
     * @param apiCategory API类别（如vocabulary, context）
     * @param apiEndpoint API端点名称（如generate-card, compact）
     * @param description 描述
     * @param conversationId 对话ID（可选）
     * @return 扣除后的余额
     * @throws BalanceException 如果余额不足
     */
    double deductBalanceWithLog(double cost, String apiCategory, String apiEndpoint, String description, Long conversationId);
    
    /**
     * 为用户充值
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 充值后的余额
     */
    double addBalance(Long userId, double amount);
    
    /**
     * 为用户充值并记录交易
     * @param userId 用户ID
     * @param amount 充值金额
     * @param description 描述
     * @return 充值后的余额
     */
    double addBalanceWithLog(Long userId, double amount, String description);
    
    /**
     * 为当前用户充值
     * @param amount 充值金额
     * @return 充值后的余额
     */
    double addCurrentUserBalance(double amount);
    
    /**
     * 获取当前用户的交易记录
     * @param pageable 分页参数
     * @return 交易记录分页
     */
    Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable);
}
