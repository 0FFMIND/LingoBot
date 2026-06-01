package com.lingobot.learning.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatRequest extends BaseChatRequest {
    
    private String content;
    private String messageType;
    private String executionMode;
    private String audioData;
    private String audioFormat;
    private Integer audioDuration;
    private String imageData;
    private String imageFormat;
    private String intent;
    private String currentWord;
    
    public static final String EXECUTION_MODE_LOOP = "loop";
    public static final String EXECUTION_MODE_ONETIME = "onetime";
    
    public static final String MESSAGE_TYPE_CHAT = "chat";
    public static final String MESSAGE_TYPE_VOCABULARY = "vocabulary";
    public static final String MESSAGE_TYPE_VOCABULARY_SENTENCE = "vocabulary_sentence";
    public static final String MESSAGE_TYPE_AUDIO = "audio";
    public static final String MESSAGE_TYPE_IMAGE = "image";
    
    public String getExecutionModeOrDefault() {
        return executionMode != null ? executionMode : EXECUTION_MODE_LOOP;
    }
    
    public String getMessageTypeOrDefault() {
        return messageType != null ? messageType : MESSAGE_TYPE_CHAT;
    }
    
    public boolean isExecutionModeOnetime() {
        return EXECUTION_MODE_ONETIME.equals(getExecutionModeOrDefault());
    }
    
    public boolean isExecutionModeLoop() {
        return EXECUTION_MODE_LOOP.equals(getExecutionModeOrDefault());
    }
    
    public boolean isMessageTypeVocabulary() {
        return MESSAGE_TYPE_VOCABULARY.equals(getMessageTypeOrDefault());
    }
    
    public boolean isAudioMessage() {
        return MESSAGE_TYPE_AUDIO.equals(messageType) && audioData != null && !audioData.isEmpty();
    }
    
    public boolean isImageMessage() {
        return MESSAGE_TYPE_IMAGE.equals(messageType) && imageData != null && !imageData.isEmpty();
    }
    
    public boolean shouldUseOnetimeExecution() {
        return isExecutionModeOnetime() || isMessageTypeVocabulary();
    }
}
