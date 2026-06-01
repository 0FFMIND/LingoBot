package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VocabularyCompactResult {
    private boolean executed;
    private String reason;
    private int beforeTokens;
    private int afterTokens;
    private int savedTokens;
    private int compactedCardsCount;
    private int recentCardsCount;
    private int totalCompactedCards;

    public static VocabularyCompactResult notExecuted(String reason) {
        return new VocabularyCompactResult(false, reason, 0, 0, 0, 0, 0, 0);
    }
}
