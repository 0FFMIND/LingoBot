package com.lingobot.learning.conversation.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyConversationDataDTO {

    private String vocabularyIntent;
    private String vocabularyCompactedSummary;
    private Long vocabularyLastCompactedCardId;
    private Integer vocabularyLastCompactedPosition;
    private LocalDateTime vocabularyLastCompactedAt;
    private Integer vocabularyCompactedCardCount;
}
