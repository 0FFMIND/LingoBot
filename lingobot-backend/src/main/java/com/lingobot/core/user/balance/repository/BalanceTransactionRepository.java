package com.lingobot.core.user.balance.repository;

import com.lingobot.core.user.balance.entity.BalanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易记录数据访问层。
 *
 * 提供 BalanceTransaction 实体的 CRUD 操作，
 * 以及按用户分页查询交易记录的方法。
 */
@Repository
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    // 按用户 ID 分页查询所有交易记录，按创建时间倒序
    Page<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 按用户 ID 和时间范围分页查询交易记录，按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<BalanceTransaction> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // 按用户 ID 分页查询收入类交易（余额增加），按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.balanceAfter > t.balanceBefore ORDER BY t.createdAt DESC")
    Page<BalanceTransaction> findIncomeByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    // 按用户 ID 分页查询支出类交易（余额减少），按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.balanceAfter < t.balanceBefore ORDER BY t.createdAt DESC")
    Page<BalanceTransaction> findExpenseByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    // 按用户 ID 和时间范围分页查询收入类交易，按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.balanceAfter > t.balanceBefore AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<BalanceTransaction> findIncomeByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // 按用户 ID 和时间范围分页查询支出类交易，按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.balanceAfter < t.balanceBefore AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<BalanceTransaction> findExpenseByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // 按用户 ID 和时间范围查询所有交易记录列表（不分页），按创建时间倒序
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<BalanceTransaction> findByUserIdAndDateRangeList(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 按用户 ID 和时间范围查询所有充值交易记录（RECHARGE 类型）
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.type = 'RECHARGE' AND t.createdAt BETWEEN :startDate AND :endDate")
    List<BalanceTransaction> findRechargeByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 按用户 ID 和时间范围查询所有扣费交易记录（CHARGE 类型）
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.type = 'CHARGE' AND t.createdAt BETWEEN :startDate AND :endDate")
    List<BalanceTransaction> findChargeByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 查询指定用户的所有充值交易记录（RECHARGE 类型）
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.type = 'RECHARGE'")
    List<BalanceTransaction> findAllRechargeByUserId(@Param("userId") Long userId);

    // 查询指定用户的所有扣费交易记录（CHARGE 类型）
    @Query("SELECT t FROM BalanceTransaction t WHERE t.user.id = :userId AND t.type = 'CHARGE'")
    List<BalanceTransaction> findAllChargeByUserId(@Param("userId") Long userId);
}
