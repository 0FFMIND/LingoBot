package com.lingobot.core.user.redemption.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 兑换码使用记录实体类。
 *
 * 对应数据库表 redemption_code_usages，
 * 记录每个兑换码被哪些用户在什么时间使用过。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "redemption_code_usages")
public class RedemptionCodeUsage {
    
    // 使用记录主键 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 关联的兑换码
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_code_id", nullable = false)
    private RedemptionCode redemptionCode;
    
    // 使用该兑换码的用户
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // 使用时间
    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
    
    // 持久化前自动设置使用时间（如果未设置）
    @PrePersist
    protected void onCreate() {
        if (usedAt == null) {
            usedAt = LocalDateTime.now();
        }
    }
}
