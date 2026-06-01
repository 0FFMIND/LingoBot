package com.lingobot.learning.chat.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

public interface MemoryCompactService {

    @Getter
    @AllArgsConstructor
    class CompactResult {
        private final boolean executed;
        private final String reason;
        private final int beforeTokens;
        private final int afterTokens;
        private final int compactedCardCount;
        private final int compactedCardsCount;
        private final int recentCardsCount;
        private final int totalCompactedCards;
        private final int compactBatch;

        public int getSavedTokens() {
            return beforeTokens - afterTokens;
        }
    }

    CompactResult executeCompact(Long conversationId);
}
