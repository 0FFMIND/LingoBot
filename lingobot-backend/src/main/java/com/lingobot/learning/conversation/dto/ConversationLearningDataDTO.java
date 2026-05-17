package com.lingobot.learning.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationLearningDataDTO {

    private String learningMode;
    private String vocabularyIntent;
    private Integer compactedCount;
    private Long totalTokensEstimate;
    private Integer vocabularyCompactedCardCount;
}
