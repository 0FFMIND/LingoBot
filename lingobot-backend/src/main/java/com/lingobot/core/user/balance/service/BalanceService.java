package com.lingobot.core.user.balance.service;

import com.lingobot.core.user.balance.dto.BalanceTransactionDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface BalanceService {

    BigDecimal getCurrentUserBalance();

    BigDecimal getCurrentUserFrozenBalance();

    boolean hasSufficientBalance(BigDecimal cost);

    BigDecimal deductBalance(BigDecimal cost);

    BigDecimal deductBalanceWithLog(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId);

    Long freezeBalance(BigDecimal cost, String apiCategory, String apiEndpoint, String description, Long conversationId);

    void confirmTransaction(Long transactionId);

    void cancelTransaction(Long transactionId);

    BigDecimal addBalance(Long userId, BigDecimal amount);

    BigDecimal addBalanceWithLog(Long userId, BigDecimal amount, String description);

    BigDecimal addCurrentUserBalance(BigDecimal amount);

    Page<BalanceTransactionDTO> getCurrentUserTransactions(Pageable pageable);

    BigDecimal getUserBalance(Long userId);

    BigDecimal getUserFrozenBalance(Long userId);

    BigDecimal setUserBalance(Long userId, BigDecimal newBalance);
}
