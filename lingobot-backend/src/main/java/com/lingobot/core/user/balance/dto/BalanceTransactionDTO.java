package com.lingobot.core.user.balance.dto;

import com.lingobot.core.user.balance.entity.BalanceTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录传输对象。
 *
 * 用于将 BalanceTransaction 实体转换为 API 响应格式，
 * 隐藏敏感信息（如用户关联），仅暴露前端所需字段。
 */
@Data
@Builder
public class BalanceTransactionDTO {

    private String publicId;
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

    // 将 BalanceTransaction 实体转换为 DTO，枚举字段转为字符串
    public static BalanceTransactionDTO fromEntity(BalanceTransaction t) {
        return BalanceTransactionDTO.builder()
                .publicId(t.getPublicId())
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
