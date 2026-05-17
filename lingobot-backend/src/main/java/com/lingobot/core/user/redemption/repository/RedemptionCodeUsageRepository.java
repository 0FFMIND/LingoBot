package com.lingobot.core.user.redemption.repository;

import com.lingobot.core.user.redemption.entity.RedemptionCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 兑换码使用记录仓库接口。
 *
 * 提供兑换码使用记录的数据库操作，包括查询指定兑换码的使用记录、
 * 统计使用次数、检查用户是否已使用等。
 */
@Repository
public interface RedemptionCodeUsageRepository extends JpaRepository<RedemptionCodeUsage, Long> {
    
    // 查询指定兑换码的所有使用记录，同时加载用户信息（按使用时间倒序）
    @Query("SELECT u FROM RedemptionCodeUsage u LEFT JOIN FETCH u.user WHERE u.redemptionCode.id = :codeId ORDER BY u.usedAt DESC")
    List<RedemptionCodeUsage> findByRedemptionCodeId(Long codeId);
    
    // 统计指定兑换码的使用次数
    long countByRedemptionCodeId(Long codeId);
    
    // 检查指定用户是否已使用过指定兑换码
    boolean existsByRedemptionCodeIdAndUserId(Long codeId, Long userId);
}
