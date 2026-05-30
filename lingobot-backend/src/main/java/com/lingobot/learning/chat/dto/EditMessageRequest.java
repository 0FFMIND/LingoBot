package com.lingobot.learning.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {
    
    private Long conversationId;
    private String conversationPublicId;
    private Long userMessageId;
    private String newContent;
    private String mode;
    private String model;
    private String learningMode;
    private String vocabularyCategory;
    private String vocabularyDifficulty;
    private String audioData;
    private String audioFormat;
    private Integer audioDuration;
    private String imageData;
    private String imageFormat;

    public String getModeOrDefault() {
        return mode != null ? mode : "chat";
    }

    public String getModelOrDefault() {
        return model != null ? model : "qwen/qwen3.5-flash-20260224";
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

    public boolean hasAudio() {
        return audioData != null && !audioData.isEmpty();
    }

    public boolean hasImage() {
        return imageData != null && !imageData.isEmpty();
    }
}
