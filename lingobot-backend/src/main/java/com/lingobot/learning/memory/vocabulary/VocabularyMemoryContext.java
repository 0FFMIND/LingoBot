package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyMemoryContext {
    @Builder.Default
    private List<VocabularyMemoryRecord> conversationRecentCards = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> recentWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> wrongWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> regeneratedWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> learningWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> reviewingWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> masteredWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> weakWords = new ArrayList<>();
    @Builder.Default
    private List<String> excludedWords = new ArrayList<>();

    private Boolean l1RecentEmpty;

    private Boolean l1WrongEmpty;

    private Boolean l1RegeneratedEmpty;

    private Long l1RecentDaysSinceLastEvent;

    private Long l1WrongDaysSinceLastEvent;

    private Long l1RegeneratedDaysSinceLastEvent;

    @Builder.Default
    private List<String> mergedL1ToConversationWords = new ArrayList<>();
    
    private String vocabularyCompactedSummary;
    
    private VocabularyCompactWatermark vocabularyCompactWatermark;

    public int totalMemoryItems() {
        return conversationRecentCards.size()
                + recentWords.size()
                + wrongWords.size()
                + regeneratedWords.size()
                + learningWords.size()
                + reviewingWords.size()
                + masteredWords.size()
                + weakWords.size();
    }
}
