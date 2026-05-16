package com.lingobot.core.user.balance.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 余额交易记录实体。
 *
 * 记录每一笔余额变动（扣费、充值、冻结等），包含交易类型、状态、
 * 变动前后余额、关联 API 信息等，便于审计和追溯。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_transactions")
public class BalanceTransaction {

    // 交易类型：CHARGE（扣费）、RECHARGE（充值）、ADMIN_ADJUSTMENT（管理员调账）
    public enum TransactionType {
        CHARGE, RECHARGE, ADMIN_ADJUSTMENT
    }

    // 交易状态：PENDING（待确认）、SUCCEEDED（成功）、FAILED（失败）、RELEASED（已释放）
    public enum TransactionStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCEEDED;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "api_category", length = 100)
    private String apiCategory;

    @Column(name = "api_endpoint", length = 255)
    private String apiEndpoint;

    @Column(length = 500)
    private String description;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 持久化前自动设置创建时间和默认状态，成功交易同时设置完成时间
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = TransactionStatus.SUCCEEDED;
        }
        if (status == TransactionStatus.SUCCEEDED && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
