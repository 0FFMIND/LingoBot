package com.lingobot.core.user.redemption.repository;

import com.lingobot.core.user.redemption.entity.RedemptionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RedemptionCodeRepository extends JpaRepository<RedemptionCode, Long> {
    
    Optional<RedemptionCode> findByCode(String code);
    
    boolean existsByCode(String code);
    
    @Query("SELECT rc FROM RedemptionCode rc LEFT JOIN FETCH rc.usedBy LEFT JOIN FETCH rc.createdBy ORDER BY rc.createdAt DESC")
    List<RedemptionCode> findAllWithDetails();
    
    @Query("SELECT rc FROM RedemptionCode rc LEFT JOIN FETCH rc.usedBy LEFT JOIN FETCH rc.createdBy WHERE rc.id = :id")
    Optional<RedemptionCode> findByIdWithDetails(@Param("id") Long id);
}
