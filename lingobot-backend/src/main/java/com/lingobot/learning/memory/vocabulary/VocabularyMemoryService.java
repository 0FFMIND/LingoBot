package com.lingobot.learning.memory.vocabulary;

import com.lingobot.learning.vocabulary.entity.VocabularyCard;

import java.util.List;

public interface VocabularyMemoryService {
    VocabularyMemoryContext retrieveMemory(Long userId, Long conversationId,
                                            VocabularyGenerationIntent intent,
                                            VocabularyGenerationConstraints constraints);

    VocabularyMemoryContext retrieveMemoryWithLimits(Long userId, Long conversationId,
                                                 VocabularyGenerationIntent intent,
                                                 int l1RecentLimit,
                                                 int l1WrongLimit,
                                                 int l1RegeneratedLimit,
                                                 int l2MasteredLimit,
                                                 int l2ReviewingLimit,
                                                 int l2LearningLimit,
                                                 int l2WeakLimit);

    List<VocabularyMemoryRecord> retrieveL1Recent(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL1Wrong(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL1Regenerated(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL2Mastered(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL2Reviewing(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL2Learning(Long userId, int limit);

    List<VocabularyMemoryRecord> retrieveL2Weak(Long userId, int limit);

    void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType);

    void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType,
                           String userAnswer, String aiFeedback, VocabularyMemoryInteractionType interactionType);
    
    VocabularyCompactWatermark compactVocabularyHistory(Long conversationId, String compactedSummary);
    
    VocabularyCompactWatermark getCompactWatermark(Long conversationId);
    
    String getCompactedSummary(Long conversationId);
}
