package com.lingobot.auth.repository;

import com.lingobot.auth.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    
    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ipAddress);
    
    List<LoginAttempt> findByUserIdOrderByAttemptTimeDesc(Long userId);
    
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.ipAddress = :ipAddress " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByIp(@Param("ipAddress") String ipAddress, 
                                  @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.user.id = :userId " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByUser(@Param("userId") Long userId, 
                                    @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE a.username = :username " +
           "AND a.success = false AND a.attemptTime >= :since")
    long countFailedAttemptsByUsername(@Param("username") String username, 
                                        @Param("since") LocalDateTime since);
    
    void deleteByAttemptTimeBefore(LocalDateTime cutoffTime);
}
