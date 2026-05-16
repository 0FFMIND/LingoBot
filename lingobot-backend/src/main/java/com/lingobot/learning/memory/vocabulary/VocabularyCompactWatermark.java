package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCompactWatermark {
    
    private Long lastCompactedCardId;
    
    private Integer lastCompactedPosition;
    
    private LocalDateTime lastCompactedAt;
    
    private Integer compactedCardCount;
    
    public boolean isEmpty() {
        return lastCompactedCardId == null && lastCompactedPosition == null;
    }
}
