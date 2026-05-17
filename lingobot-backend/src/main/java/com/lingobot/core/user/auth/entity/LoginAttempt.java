package com.lingobot.core.user.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录尝试记录实体。
 *
 * 记录每次登录尝试的详细信息，包括 IP 地址、用户、是否成功、失败原因等。
 * 用于登录失败次数统计和安全审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {
    
    // 主键 ID，自增生成
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 登录尝试的 IP 地址（支持 IPv4 和 IPv6，最长 45 字符）
    @Column(length = 45)
    private String ipAddress;
    
    // 关联的用户（如果尝试的用户名存在）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    // 尝试登录时使用的用户名
    private String username;
    
    // 登录是否成功
    @Column(nullable = false)
    private boolean success;
    
    // 登录失败的原因（如密码错误、用户不存在等）
    private String failureReason;
    
    // 登录尝试的时间，首次保存时自动设置
    @Column(nullable = false, updatable = false)
    private LocalDateTime attemptTime;
    
    // 实体持久化前自动设置尝试时间（如果未设置）
    @PrePersist
    protected void onCreate() {
        if (attemptTime == null) {
            attemptTime = LocalDateTime.now();
        }
    }
}
