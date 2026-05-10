package com.lingobot.core.user.balance.service.impl;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.balance.dto.BalanceTransactionDTO;
import com.lingobot.core.user.balance.entity.BalanceTransaction;
import com.lingobot.core.user.balance.entity.UserBalance;
import com.lingobot.core.user.balance.repository.BalanceTransactionRepository;
import com.lingobot.core.user.balance.repository.UserBalanceRepository;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.exception.AuthException;
import com.lingobot.infrastructure.common.exception.BalanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final UserRepository userRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final BalanceTransactionRepository transactionRepository;

    @Override
    public BigDecimal getCurrentUserBalance() {
        UserBalance userBalance = getOrCreateCurrentUserBalance();
        return userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getCurrentUserFrozenBalance() {
        UserBalance userBalance = getOrCreateCurrentUserBalance();
        return userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;
    }

    @Override
    public boolean hasSufficientBalance(BigDecimal cost) {
        BigDecimal currentBalance = getCurrentUserBalance();
        return currentBalance.compareTo(cost) >= 0;
    }

    @Override
    @Transactional
    public BigDecimal deductBalance(BigDecimal cost) {
        return deductBalanceWithLog(cost, null, null, "扣费", null);
    }

    @Override
    @Transactional
    public BigDecimal deductBalanceWithLog(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
        UserBalance userBalance = getOrCreateUserBalanceForUpdate(user.getId());
        BigDecimal currentBalance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;

        if (currentBalance.compareTo(cost) < 0) {
            log.warn("用户余额不足。用户: {}, 当前余额: {}, 需要: {}",
                    user.getUsername(), currentBalance, cost);
            throw BalanceException.insufficientBalance("余额不足。当前余额: " + currentBalance + "，需要: " + cost);
        }

        BigDecimal newBalance = currentBalance.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        userBalance.setBalance(newBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .type(BalanceTransaction.TransactionType.CHARGE)
                .amount(cost)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .status(BalanceTransaction.TransactionStatus.SUCCEEDED)
                .completedAt(LocalDateTime.now())
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
    public Long freezeBalance(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
        UserBalance userBalance = getOrCreateUserBalanceForUpdate(user.getId());
        BigDecimal currentBalance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
        BigDecimal currentFrozenBalance = userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;

        if (currentBalance.compareTo(cost) < 0) {
            log.warn("用户余额不足，无法冻结。用户: {}, 当前余额: {}, 需要: {}",
                    user.getUsername(), currentBalance, cost);
            throw BalanceException.insufficientBalance("余额不足。当前余额: " + currentBalance + "，需要: " + cost);
        }

        BigDecimal newBalance = currentBalance.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newFrozenBalance = currentFrozenBalance.add(cost).setScale(2, RoundingMode.HALF_UP);
        userBalance.setBalance(newBalance);
        userBalance.setFrozenBalance(newFrozenBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .type(BalanceTransaction.TransactionType.CHARGE)
                .amount(cost)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .status(BalanceTransaction.TransactionStatus.PENDING)
                .apiCategory(apiCategory)
                .apiEndpoint(apiEndpoint)
                .description(description)
                .conversationId(conversationId)
                .build();
        transactionRepository.save(transaction);

        log.info("用户余额冻结成功。用户: {}, 冻结: {}, 剩余可用: {}, 冻结总额: {}, 类别: {}, 端点: {}",
                user.getUsername(), cost, newBalance, newFrozenBalance, apiCategory, apiEndpoint);

        return transaction.getId();
    }

    @Override
    @Transactional
    public void confirmTransaction(Long transactionId) {
        BalanceTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("交易记录不存在: " + transactionId));

        if (transaction.getStatus() != BalanceTransaction.TransactionStatus.PENDING) {
            log.warn("交易状态不是 PENDING，无法确认。交易ID: {}, 当前状态: {}", transactionId, transaction.getStatus());
            return;
        }

        User transactionUser = transaction.getUser();
        if (transactionUser == null) {
            throw new IllegalStateException("交易没有关联用户: " + transactionId);
        }

        UserBalance userBalance = getOrCreateUserBalanceForUpdate(transactionUser.getId());
        BigDecimal cost = transaction.getAmount();
        BigDecimal currentFrozenBalance = userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;

        if (currentFrozenBalance.compareTo(cost) < 0) {
            log.error("冻结余额不足，无法确认交易。用户: {}, 冻结余额: {}, 需要: {}",
                    transactionUser.getUsername(), currentFrozenBalance, cost);
            throw new IllegalStateException("冻结余额不足");
        }

        BigDecimal newFrozenBalance = currentFrozenBalance.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        userBalance.setFrozenBalance(newFrozenBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        transaction.setStatus(BalanceTransaction.TransactionStatus.SUCCEEDED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("交易确认成功。交易ID: {}, 用户: {}, 扣费: {}, 剩余冻结: {}",
                transactionId, transactionUser.getUsername(), cost, newFrozenBalance);
    }

    @Override
    @Transactional
    public void cancelTransaction(Long transactionId) {
        BalanceTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("交易记录不存在: " + transactionId));

        if (transaction.getStatus() != BalanceTransaction.TransactionStatus.PENDING) {
            log.warn("交易状态不是 PENDING，无法取消。交易ID: {}, 当前状态: {}", transactionId, transaction.getStatus());
            return;
        }

        User transactionUser = transaction.getUser();
        if (transactionUser == null) {
            throw new IllegalStateException("交易没有关联用户: " + transactionId);
        }

        UserBalance userBalance = getOrCreateUserBalanceForUpdate(transactionUser.getId());
        BigDecimal cost = transaction.getAmount();
        BigDecimal currentBalance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
        BigDecimal currentFrozenBalance = userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;

        if (currentFrozenBalance.compareTo(cost) < 0) {
            log.error("冻结余额不足，无法取消交易。用户: {}, 冻结余额: {}, 需要: {}",
                    transactionUser.getUsername(), currentFrozenBalance, cost);
            throw new IllegalStateException("冻结余额不足");
        }

        BigDecimal newBalance = currentBalance.add(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newFrozenBalance = currentFrozenBalance.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        userBalance.setBalance(newBalance);
        userBalance.setFrozenBalance(newFrozenBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        transaction.setStatus(BalanceTransaction.TransactionStatus.RELEASED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("交易取消成功，余额已返还。交易ID: {}, 用户: {}, 返还: {}, 新余额: {}, 剩余冻结: {}",
                transactionId, transactionUser.getUsername(), cost, newBalance, newFrozenBalance);
    }

    @Override
    @Transactional
    public BigDecimal addBalance(Long userId, BigDecimal amount) {
        return addBalanceWithLog(userId, amount, "充值");
    }

    @Override
    @Transactional
    public BigDecimal addBalanceWithLog(Long userId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw AuthException.badRequest("充值金额必须大于0");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("用户不存在"));

        UserBalance userBalance = getOrCreateUserBalanceForUpdate(userId);
        BigDecimal currentBalance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(amount).setScale(2, RoundingMode.HALF_UP);
        userBalance.setBalance(newBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .type(BalanceTransaction.TransactionType.RECHARGE)
                .amount(amount)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .status(BalanceTransaction.TransactionStatus.SUCCEEDED)
                .completedAt(LocalDateTime.now())
                .description(description)
                .build();
        transactionRepository.save(transaction);

        log.info("用户充值成功。用户: {}, 充值: {}, 新余额: {}",
                user.getUsername(), amount, newBalance);

        return newBalance;
    }

    @Override
    @Transactional
    public BigDecimal addCurrentUserBalance(BigDecimal amount) {
        User user = getCurrentUser();
        return addBalanceWithLog(user.getId(), amount, "充值");
    }

    @Override
    public Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable) {
        User user = getCurrentUser();
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(BalanceTransactionDTO::fromEntity);
    }

    @Override
    public BigDecimal getUserBalance(Long userId) {
        UserBalance userBalance = userBalanceRepository.findByUserId(userId).orElse(null);
        if (userBalance == null) {
            return BigDecimal.ZERO;
        }
        return userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getUserFrozenBalance(Long userId) {
        UserBalance userBalance = userBalanceRepository.findByUserId(userId).orElse(null);
        if (userBalance == null) {
            return BigDecimal.ZERO;
        }
        return userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public BigDecimal setUserBalance(Long userId, BigDecimal newBalance) {
        if (newBalance == null) {
            throw new IllegalArgumentException("余额不能为空");
        }
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("余额不能为负数");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        UserBalance userBalance = getOrCreateUserBalanceForUpdate(userId);
        BigDecimal oldBalance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
        userBalance.setBalance(newBalance);
        userBalanceRepository.saveAndFlush(userBalance);

        log.info("管理员修改用户余额: userId={}, username={}, 旧余额={}, 新余额={}",
                userId, user.getUsername(), oldBalance, newBalance);

        return newBalance;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
    }

    private UserBalance getOrCreateCurrentUserBalance() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
        return getOrCreateUserBalance(user.getId());
    }

    private UserBalance getOrCreateUserBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
                    UserBalance newBalance = UserBalance.builder()
                            .user(user)
                            .balance(BigDecimal.ZERO)
                            .frozenBalance(BigDecimal.ZERO)
                            .build();
                    return userBalanceRepository.save(newBalance);
                });
    }

    private UserBalance getOrCreateUserBalanceForUpdate(Long userId) {
        return userBalanceRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> AuthException.userNotFound("用户不存在"));
                    UserBalance newBalance = UserBalance.builder()
                            .user(user)
                            .balance(BigDecimal.ZERO)
                            .frozenBalance(BigDecimal.ZERO)
                            .build();
                    return userBalanceRepository.save(newBalance);
                });
    }
}
