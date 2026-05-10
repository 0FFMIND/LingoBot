package com.lingobot.core.user.redemption.repository;

import com.lingobot.core.user.redemption.entity.RedemptionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 兑换码数据访问层。
 *
 * 继承 JpaRepository 提供基础 CRUD 操作，额外定义了：
 * - 按 code 查询的方法（用于用户兑换）
 * - 检查 code 是否存在的方法（用于生成唯一兑换码）
 * - 两个带 JOIN FETCH 的查询方法，用于一次性加载懒加载关联
 *
 * JOIN FETCH 说明：
 * usedBy 和 createdBy 都是 LAZY 关联，
 * DTO 转换时需要访问这两个对象的 id/username，
 * 使用 LEFT JOIN FETCH 在一次查询中加载，避免 N+1 问题。
 */
@Repository
public interface RedemptionCodeRepository extends JpaRepository<RedemptionCode, Long> {
    
    // 按兑换码字符串精确查询
    Optional<RedemptionCode> findByCode(String code);
    
    // 检查兑换码是否存在（用于生成唯一码时的循环校验）
    boolean existsByCode(String code);
    
    // 查询所有兑换码并 FETCH 关联对象，按创建时间倒序排列
    @Query("SELECT rc FROM RedemptionCode rc LEFT JOIN FETCH rc.usedBy LEFT JOIN FETCH rc.createdBy ORDER BY rc.createdAt DESC")
    List<RedemptionCode> findAllWithDetails();
    
    // 按ID查询单个兑换码并 FETCH 关联对象
    @Query("SELECT rc FROM RedemptionCode rc LEFT JOIN FETCH rc.usedBy LEFT JOIN FETCH rc.createdBy WHERE rc.id = :id")
    Optional<RedemptionCode> findByIdWithDetails(@Param("id") Long id);
}
