package com.lingobot.core.user.auth.service.impl;

import com.lingobot.core.user.auth.dto.BalanceTransactionDTO;
import com.lingobot.core.user.auth.entity.BalanceTransaction;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.BalanceTransactionRepository;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.BalanceService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.infrastructure.common.exception.InsufficientBalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;

    @Override
    public double getCurrentUserBalance() {
        User user = getCurrentUser();
        return user.getBalance() != null ? user.getBalance() : 0.0;
    }

    @Override
    public boolean hasSufficientBalance(double cost) {
        double currentBalance = getCurrentUserBalance();
        return currentBalance >= cost;
    }

    @Override
    @Transactional
    public double deductBalance(double cost) {
        return deductBalanceWithLog(cost, null, null, "扣费", null);
    }

    @Override
    @Transactional
    public double deductBalanceWithLog(double cost, String apiCategory, String apiEndpoint, String description, Long conversationId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
        double currentBalance = user.getBalance() != null ? user.getBalance() : 0.0;

        if (currentBalance < cost) {
            log.warn("用户余额不足。用户: {}, 当前余额: {}, 需要: {}",
                    user.getUsername(), currentBalance, cost);
            throw new InsufficientBalanceException("你的余额不足", currentBalance, cost);
        }

        double newBalance = roundToTwoDecimals(currentBalance - cost);
        user.setBalance(newBalance);
        userRepository.saveAndFlush(user);

        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .type(BalanceTransaction.TransactionType.CHARGE)
                .amount(cost)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .apiCategory(apiCategory)
                .apiEndpoint(apiEndpoint)
                .description(description)
                .conversationId(conversationId)
                .build();
        transactionRepository.save(transaction);

        log.info("用户扣费成功。用户: {}, 扣除: {}, 剩余: {}, 类别: {}, 端点: {}",
                user.getUsername(), cost, newBalance, apiCategory, apiEndpoint);

        return newBalance;
    }

    @Override
    @Transactional
    public double addBalance(Long userId, double amount) {
        return addBalanceWithLog(userId, amount, "充值");
    }

    @Override
    @Transactional
    public double addBalanceWithLog(Long userId, double amount, String description) {
        if (amount <= 0) {
            throw new ChatException("充值金额必须大于0");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException("用户不存在"));

        double currentBalance = user.getBalance() != null ? user.getBalance() : 0.0;
        double newBalance = roundToTwoDecimals(currentBalance + amount);
        user.setBalance(newBalance);
        userRepository.saveAndFlush(user);

        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .type(BalanceTransaction.TransactionType.RECHARGE)
                .amount(amount)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .description(description)
                .build();
        transactionRepository.save(transaction);

        log.info("用户充值成功。用户: {}, 充值: {}, 新余额: {}",
                user.getUsername(), amount, newBalance);

        return newBalance;
    }

    @Override
    @Transactional
    public double addCurrentUserBalance(double amount) {
        User user = getCurrentUser();
        return addBalanceWithLog(user.getId(), amount, "充值");
    }

    @Override
    public Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable) {
        User user = getCurrentUser();
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(BalanceTransactionDTO::fromEntity);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("用户不存在"));
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
