package com.lingobot.learning.vocabulary.graph;

import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import org.bsc.langgraph4j.state.AgentState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VocabularyState extends AgentState {

    public VocabularyState(Map<String, Object> initData) {
        super(initData);
    }

    public Long getConversationId() {
        return value("conversationId").map(Long.class::cast).orElse(null);
    }

    public Long getUserId() {
        return value("userId").map(Long.class::cast).orElse(null);
    }

    public String getCategory() {
        return value("category").map(String.class::cast).orElse(null);
    }

    public String getDifficulty() {
        return value("difficulty").map(String.class::cast).orElse(null);
    }

    public VocabularyGenerationIntent getIntent() {
        return value("intent").map(VocabularyGenerationIntent.class::cast).orElse(null);
    }

    public String getModel() {
        return value("model").map(String.class::cast).orElse(null);
    }

    public VocabularyMemoryContext getMemoryContext() {
        return value("memoryContext").map(VocabularyMemoryContext.class::cast).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getExcludedWords() {
        return value("excludedWords")
                .map(obj -> (List<String>) obj)
                .orElse(new ArrayList<>());
    }

    public VocabularyGenerationConstraints getConstraints() {
        return value("constraints").map(VocabularyGenerationConstraints.class::cast).orElse(null);
    }

    public String getSystemPrompt() {
        return value("systemPrompt").map(String.class::cast).orElse(null);
    }

    public WordCardData getGeneratedCard() {
        return value("generatedCard").map(WordCardData.class::cast).orElse(null);
    }

    public TokenUsageDTO getTokenUsage() {
        return value("tokenUsage").map(TokenUsageDTO.class::cast).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getValidationErrors() {
        return value("validationErrors")
                .map(obj -> (List<String>) obj)
                .orElse(new ArrayList<>());
    }

    public boolean isValid() {
        return value("isValid").map(Boolean.class::cast).orElse(false);
    }

    public VocabularyCardDTO getSavedCard() {
        return value("savedCard").map(VocabularyCardDTO.class::cast).orElse(null);
    }

    public int getRetryCount() {
        return value("retryCount").map(Integer.class::cast).orElse(0);
    }
}
