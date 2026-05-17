package com.lingobot.core.user.auth.repository;

import com.lingobot.core.user.auth.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录尝试记录仓库接口。
 *
 * 提供登录尝试记录的数据库操作，包括按 IP/用户查询、
 * 统计指定时间范围内的失败次数、清理历史记录等。
 */
@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    
    // 根据 IP 地址查询登录尝试记录（按尝试时间倒序）
    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ipAddress);
    
    // 根据用户 ID 查询登录尝试记录（按尝试时间倒序）
    List<LoginAttempt> findByUserIdOrderByAttemptTimeDesc(Long userId);
    
    // 统计指定 IP 在指定时间后的失败登录次数
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.ipAddress = :ipAddress " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByIp(@Param("ipAddress") String ipAddress, 
                                  @Param("since") LocalDateTime since);
    
    // 统计指定用户在指定时间后的失败登录次数
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.user.id = :userId " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByUser(@Param("userId") Long userId, 
                                    @Param("since") LocalDateTime since);
    
    // 统计指定用户名在指定时间后的失败登录次数
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.username = :username " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByUsername(@Param("username") String username, 
                                        @Param("since") LocalDateTime since);
    
    // 删除指定时间之前的登录尝试记录（用于定期清理）
    void deleteByAttemptTimeBefore(LocalDateTime cutoffTime);
}
