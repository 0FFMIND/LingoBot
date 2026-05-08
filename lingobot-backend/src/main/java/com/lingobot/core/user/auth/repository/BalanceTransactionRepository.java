package com.lingobot.core.user.auth.repository;

import com.lingobot.core.user.auth.entity.BalanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    Page<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
