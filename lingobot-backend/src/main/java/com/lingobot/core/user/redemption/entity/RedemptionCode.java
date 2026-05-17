package com.lingobot.core.user.redemption.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 兑换码实体类。
 *
 * 对应数据库表 redemption_codes，存储兑换码的基本信息、
 * 面额、使用次数限制、过期时间等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "redemption_codes")
public class RedemptionCode {
    
    // 兑换码主键 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 兑换码字符串（格式：sk- + UUID，唯一）
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    
    // 兑换码可兑换的点数
    @Column(nullable = false)
    private Integer points;
    
    // 最大使用次数，null 表示无限制
    @Column(name = "max_usages")
    private Integer maxUsages;
    
    // 使用记录列表
    @OneToMany(mappedBy = "redemptionCode", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RedemptionCodeUsage> usages = new ArrayList<>();
    
    // 创建该兑换码的管理员用户
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    
    // 创建时间
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 过期时间，null 表示永不过期
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    // 是否已被使用（预留字段）
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;
    
    // 持久化前自动设置创建时间
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // 判断兑换码是否已过期
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    // 判断兑换码是否已达到最大使用次数
    public boolean isFullyUsed() {
        if (maxUsages == null) {
            return false;
        }
        return usages != null && usages.size() >= maxUsages;
    }
    
    // 获取当前已使用次数
    public int getUsageCount() {
        return usages != null ? usages.size() : 0;
    }
    
    // 判断指定用户是否已使用过该兑换码
    public boolean hasUserUsed(Long userId) {
        if (usages == null || userId == null) {
            return false;
        }
        return usages.stream()
                .anyMatch(usage -> usage.getUser() != null && userId.equals(usage.getUser().getId()));
    }
}
