package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCompactWatermark implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private Long lastCompactedCardId;
    
    private Integer lastCompactedPosition;
    
    private LocalDateTime lastCompactedAt;
    
    private Integer compactedCardCount;
    
    public boolean isEmpty() {
        return lastCompactedCardId == null && lastCompactedPosition == null;
    }
}
