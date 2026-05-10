package com.lingobot.core.user.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_transactions")
public class BalanceTransaction {

    public enum TransactionType {
        CHARGE, RECHARGE
    }

    public enum TransactionStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.SUCCEEDED;
        }
        if (status == TransactionStatus.SUCCEEDED && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
