package com.lingobot.core.user.balance.service;

import com.lingobot.core.user.balance.dto.BalanceTransactionDTO;
import com.lingobot.core.user.balance.dto.TransactionSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * 余额服务接口。
 *
 * 定义用户余额的核心操作：查询、实时扣费、冻结-确认模式、充值等。
 * 所有余额变动操作都保证并发安全（使用悲观写锁）并记录交易流水。
 */
public interface BalanceService {

    // 获取当前登录用户的可用余额
    BigDecimal getCurrentUserBalance();

    // 获取当前登录用户的冻结余额
    BigDecimal getCurrentUserFrozenBalance();

    // 检查当前用户可用余额是否足够支付指定金额
    boolean hasSufficientBalance(BigDecimal cost);

    // 从当前用户可用余额中扣除指定金额
    BigDecimal deductBalance(BigDecimal cost);

    // 从当前用户可用余额中扣除指定金额，并记录详细的审计信息
    BigDecimal deductBalance(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId);

    // 冻结当前用户指定金额的余额（移至冻结余额），返回交易 ID，用于后续确认或取消
    Long freezeBalance(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId);

    // 确认冻结交易，从冻结余额中扣除
    void confirmTransaction(Long transactionId);

    // 取消冻结交易，将金额从冻结余额返还至可用余额
    void cancelTransaction(Long transactionId);

    // 给指定用户充值指定金额（默认描述为"充值"）
    BigDecimal addBalance(Long userId, BigDecimal amount);

    // 给指定用户充值指定金额，并使用自定义描述记录交易
    BigDecimal addBalanceWithLog(Long userId, BigDecimal amount, String description);

    // 给当前登录用户充值指定金额
    BigDecimal addCurrentUserBalance(BigDecimal amount);

    // 获取当前登录用户的交易记录（分页）
    Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable);

    // 获取当前登录用户指定时间范围内的交易记录（分页）
    Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    // 获取当前登录用户的交易记录，支持按类型（收入/支出）和时间范围筛选（分页）
    Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable, String type, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    // 获取当前登录用户的交易汇总统计
    TransactionSummaryDTO getCurrentUserTransactionSummary(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    // 获取指定用户的可用余额
    BigDecimal getUserBalance(Long userId);

    // 获取指定用户的冻结余额
    BigDecimal getUserFrozenBalance(Long userId);

    // 管理员手动设置指定用户的余额（不产生交易记录）
    BigDecimal setUserBalance(Long userId, BigDecimal newBalance);
}
