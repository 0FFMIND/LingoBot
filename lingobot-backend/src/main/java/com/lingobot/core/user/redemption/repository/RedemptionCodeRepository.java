package com.lingobot.core.user.redemption.repository;

import com.lingobot.core.user.redemption.entity.RedemptionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 兑换码仓库接口。
 *
 * 提供兑换码的数据库操作，包括基础的 CRUD 和
 * 带关联信息（使用记录、创建者）的查询方法。
 */
@Repository
public interface RedemptionCodeRepository extends JpaRepository<RedemptionCode, Long> {
    
    // 根据兑换码字符串查询
    Optional<RedemptionCode> findByCode(String code);
    
    // 检查兑换码字符串是否已存在
    boolean existsByCode(String code);
    
    // 查询所有兑换码，同时加载使用记录、使用用户和创建者信息（按创建时间倒序）
    @Query("SELECT rc FROM RedemptionCode rc " +
           "LEFT JOIN FETCH rc.usages u " +
           "LEFT JOIN FETCH u.user " +
           "LEFT JOIN FETCH rc.createdBy " +
           "ORDER BY rc.createdAt DESC")
    List<RedemptionCode> findAllWithDetails();
    
    // 根据 ID 查询兑换码，同时加载使用记录、使用用户和创建者信息
    @Query("SELECT rc FROM RedemptionCode rc " +
           "LEFT JOIN FETCH rc.usages u " +
           "LEFT JOIN FETCH u.user " +
           "LEFT JOIN FETCH rc.createdBy " +
           "WHERE rc.id = :id")
    Optional<RedemptionCode> findByIdWithDetails(@Param("id") Long id);
    
    // 根据兑换码字符串查询，同时加载使用记录、使用用户和创建者信息
    @Query("SELECT rc FROM RedemptionCode rc " +
           "LEFT JOIN FETCH rc.usages u " +
           "LEFT JOIN FETCH u.user " +
           "LEFT JOIN FETCH rc.createdBy " +
           "WHERE rc.code = :code")
    Optional<RedemptionCode> findByCodeWithDetails(@Param("code") String code);
}
