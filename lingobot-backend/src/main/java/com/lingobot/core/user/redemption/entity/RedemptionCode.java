package com.lingobot.core.user.redemption.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 兑换码实体类。
 *
 * 兑换码由管理员创建，用于用户兑换积分/点数。
 * 每个兑换码包含唯一编号、点数、使用状态、创建者、使用者、过期时间等信息。
 *
 * @ManyToOne 关系说明：
 * - createdBy：创建该兑换码的管理员（非空）
 * - usedBy：使用该兑换码的用户（未使用时为null）
 * 两个关系都使用 FetchType.LAZY 懒加载，避免不必要的性能开销。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "redemption_codes")
public class RedemptionCode {
    
    // 主键ID，自增生成
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 兑换码字符串，格式为 sk- + UUID，全局唯一
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    
    // 兑换码可兑换的点数，必须大于0
    @Column(nullable = false)
    private Integer points;
    
    // 是否已使用标志，默认false
    @Column(nullable = false)
    @Builder.Default
    private Boolean isUsed = false;
    
    // 使用该兑换码的用户，未使用时为null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    private User usedBy;
    
    // 使用时间，用户兑换成功时设置
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    // 创建该兑换码的管理员，不能为空
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    
    // 创建时间，由 @PrePersist 自动设置，不可更新
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 过期时间，可为null表示永不过期
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    // 持久化前自动设置创建时间和默认使用状态
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isUsed == null) {
            isUsed = false;
        }
    }
    
    // 判断兑换码是否已过期：设置了过期时间且当前时间已超过
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
