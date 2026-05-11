package com.lingobot.core.user.balance.repository;

import com.lingobot.core.user.balance.entity.UserBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户余额数据访问层。
 *
 * 提供 UserBalance 实体的 CRUD 操作，
 * 包含普通查询和带悲观写锁的查询（用于余额变动时防止并发问题）。
 */
@Repository
public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {

    // 按用户 ID 查询余额记录
    Optional<UserBalance> findByUserId(Long userId);

    // 按用户 ID 查询余额记录并加悲观写锁，用于并发更新场景
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ub FROM UserBalance ub WHERE ub.user.id = :userId")
    Optional<UserBalance> findByUserIdForUpdate(Long userId);
}
