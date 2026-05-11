package com.lingobot.core.user.balance.repository;

import com.lingobot.core.user.balance.entity.BalanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 交易记录数据访问层。
 *
 * 提供 BalanceTransaction 实体的 CRUD 操作，
 * 以及按用户分页查询交易记录的方法。
 */
@Repository
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    // 按用户 ID 分页查询交易记录，按创建时间倒序排列
    Page<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
