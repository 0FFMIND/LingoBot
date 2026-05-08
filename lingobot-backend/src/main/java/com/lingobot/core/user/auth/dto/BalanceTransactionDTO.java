package com.lingobot.core.user.auth.dto;

import com.lingobot.core.user.auth.entity.BalanceTransaction;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class BalanceTransactionDTO {

    private Long id;
    private String type;
    private Double amount;
    private Double balanceBefore;
    private Double balanceAfter;
    private String apiCategory;
    private String apiEndpoint;
    private String description;
    private Long conversationId;
    private LocalDateTime createdAt;

    public static BalanceTransactionDTO fromEntity(BalanceTransaction t) {
        return BalanceTransactionDTO.builder()
                .id(t.getId())
                .type(t.getType() != null ? t.getType().name() : null)
                .amount(t.getAmount())
                .balanceBefore(t.getBalanceBefore())
                .balanceAfter(t.getBalanceAfter())
                .apiCategory(t.getApiCategory())
                .apiEndpoint(t.getApiEndpoint())
                .description(t.getDescription())
                .conversationId(t.getConversationId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
