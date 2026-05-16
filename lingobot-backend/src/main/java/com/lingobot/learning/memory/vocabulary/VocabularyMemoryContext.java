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
    private List<VocabularyMemoryRecord> masteredWords = new ArrayList<>();
    @Builder.Default
    private List<VocabularyMemoryRecord> weakWords = new ArrayList<>();
    @Builder.Default
    private List<String> excludedWords = new ArrayList<>();

    public int totalMemoryItems() {
        return conversationRecentCards.size()
                + recentWords.size()
                + wrongWords.size()
                + regeneratedWords.size()
                + masteredWords.size()
                + weakWords.size();
    }
}
