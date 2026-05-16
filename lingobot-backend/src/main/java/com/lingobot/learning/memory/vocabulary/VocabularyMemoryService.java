package com.lingobot.learning.memory.vocabulary;

import com.lingobot.learning.vocabulary.entity.VocabularyCard;

public interface VocabularyMemoryService {
    VocabularyMemoryContext retrieveMemory(Long userId, Long conversationId,
                                            VocabularyGenerationIntent intent,
                                            VocabularyGenerationConstraints constraints);

    void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType);
}
