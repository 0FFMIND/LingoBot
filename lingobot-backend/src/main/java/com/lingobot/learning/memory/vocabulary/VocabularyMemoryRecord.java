package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyMemoryRecord {
    private String word;
    private String meaning;
    private String partOfSpeech;
    private BigDecimal masteryScore;
    private LocalDateTime lastReviewedAt;
    private int reviewCount;
    private boolean isMastered;
    private VocabularyMemoryEventType eventType;
    private LocalDateTime eventTimestamp;
}
