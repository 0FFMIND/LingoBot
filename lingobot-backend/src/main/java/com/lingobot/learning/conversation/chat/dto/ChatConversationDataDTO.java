package com.lingobot.learning.conversation.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationDataDTO {

    private String compactedSummary;
    private Integer compactedCount;
    private LocalDateTime lastCompactedAt;
    private Long totalTokensEstimate;
}
