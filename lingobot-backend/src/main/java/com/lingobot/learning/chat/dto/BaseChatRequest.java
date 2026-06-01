package com.lingobot.learning.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseChatRequest {

    public static final String DEFAULT_LEARNING_MODE = "chat";
    public static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    public static final String DEFAULT_VOCABULARY_DIFFICULTY = "b2";
    public static final String DEFAULT_MODE = "chat";

    private Long conversationId;
    private String conversationPublicId;
    private String mode;
    private String model;
    private String learningMode;
    private String vocabularyCategory;
    private String vocabularyDifficulty;

    public String getLearningModeOrDefault() {
        return learningMode != null ? learningMode : DEFAULT_LEARNING_MODE;
    }

    public String getVocabularyCategoryOrDefault() {
        return vocabularyCategory != null ? vocabularyCategory : DEFAULT_VOCABULARY_CATEGORY;
    }

    public String getVocabularyDifficultyOrDefault() {
        return vocabularyDifficulty != null ? vocabularyDifficulty : DEFAULT_VOCABULARY_DIFFICULTY;
    }

    public String getModeOrDefault() {
        return mode != null ? mode : DEFAULT_MODE;
    }
}
