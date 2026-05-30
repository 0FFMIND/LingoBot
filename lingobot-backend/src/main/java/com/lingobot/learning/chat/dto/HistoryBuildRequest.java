package com.lingobot.learning.chat.dto;

import com.lingobot.core.conversation.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryBuildRequest {
    
    private Long conversationId;
    private List<Message> messages;
    private Integer endIndex;
    private String learningMode;
    private String vocabularyCategory;
    private String vocabularyDifficulty;
    
    public static HistoryBuildRequest forConversation(Long conversationId, String learningMode) {
        return HistoryBuildRequest.builder()
                .conversationId(conversationId)
                .learningMode(learningMode)
                .build();
    }
    
    public static HistoryBuildRequest forMessages(List<Message> messages, int endIndex) {
        return HistoryBuildRequest.builder()
                .messages(messages)
                .endIndex(endIndex)
                .learningMode("chat")
                .build();
    }
    
    public String getLearningModeOrDefault() {
        return learningMode != null ? learningMode : "chat";
    }
    
    public int getEndIndexOrDefault() {
        return endIndex != null ? endIndex : (messages != null ? messages.size() : 0);
    }
}
