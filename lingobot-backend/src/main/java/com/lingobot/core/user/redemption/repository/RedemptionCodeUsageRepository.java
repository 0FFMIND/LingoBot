package com.lingobot.core.user.redemption.repository;

import com.lingobot.core.user.redemption.entity.RedemptionCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RedemptionCodeUsageRepository extends JpaRepository<RedemptionCodeUsage, Long> {
    
    @Query("SELECT u FROM RedemptionCodeUsage u LEFT JOIN FETCH u.user WHERE u.redemptionCode.id = :codeId ORDER BY u.usedAt DESC")
    List<RedemptionCodeUsage> findByRedemptionCodeId(Long codeId);
    
    long countByRedemptionCodeId(Long codeId);
    
    boolean existsByRedemptionCodeIdAndUserId(Long codeId, Long userId);
}
