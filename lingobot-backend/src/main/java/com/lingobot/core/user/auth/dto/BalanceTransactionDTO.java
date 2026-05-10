package com.lingobot.core.user.auth.dto;

import com.lingobot.core.user.auth.entity.BalanceTransaction;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BalanceTransactionDTO {

    private Long id;
    private String type;
    private String status;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String apiCategory;
    private String apiEndpoint;
    private String description;
    private Long conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static BalanceTransactionDTO fromEntity(BalanceTransaction t) {
        return BalanceTransactionDTO.builder()
                .id(t.getId())
                .type(t.getType() != null ? t.getType().name() : null)
                .status(t.getStatus() != null ? t.getStatus().name() : null)
                .amount(t.getAmount())
                .balanceBefore(t.getBalanceBefore())
                .balanceAfter(t.getBalanceAfter())
                .apiCategory(t.getApiCategory())
                .apiEndpoint(t.getApiEndpoint())
                .description(t.getDescription())
                .conversationId(t.getConversationId())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
