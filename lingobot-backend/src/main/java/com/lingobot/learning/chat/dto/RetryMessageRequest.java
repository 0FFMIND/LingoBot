package com.lingobot.learning.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryMessageRequest {
    
    private Long conversationId;
    private String conversationPublicId;
    private Long assistantMessageId;
    private String model;
    private String mode;
    private String learningMode;
    private String vocabularyCategory;
    private String vocabularyDifficulty;
    
    public String getModelOrDefault() {
        return model != null ? model : "qwen/qwen3.5-flash-20260224";
    }
    
    public String getModeOrDefault() {
        return mode != null ? mode : "chat";
    }
    
    public String getLearningModeOrDefault() {
        return learningMode != null ? learningMode : "chat";
    }
    
    public String getVocabularyCategoryOrDefault() {
        return vocabularyCategory != null ? vocabularyCategory : "cefr";
    }
    
    public String getVocabularyDifficultyOrDefault() {
        return vocabularyDifficulty != null ? vocabularyDifficulty : "b2";
    }
}
