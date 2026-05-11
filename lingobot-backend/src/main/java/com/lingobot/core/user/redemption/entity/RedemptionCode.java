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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "redemption_codes")
public class RedemptionCode {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    
    @Column(nullable = false)
    private Integer points;
    
    @Column(name = "max_usages")
    private Integer maxUsages;
    
    @OneToMany(mappedBy = "redemptionCode", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RedemptionCodeUsage> usages = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isFullyUsed() {
        if (maxUsages == null) {
            return false;
        }
        return usages != null && usages.size() >= maxUsages;
    }
    
    public int getUsageCount() {
        return usages != null ? usages.size() : 0;
    }
    
    public boolean hasUserUsed(Long userId) {
        if (usages == null || userId == null) {
            return false;
        }
        return usages.stream()
                .anyMatch(usage -> usage.getUser() != null && userId.equals(usage.getUser().getId()));
    }
}
