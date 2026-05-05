package com.lingobot.learning.chat.dto;

import com.lingobot.core.conversation.dto.MessageDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {
    
    private String type;
    private String content;
    private boolean done;
    private MessageDTO message;
    private String toolName;
    private String toolId;
    private boolean toolSuccess;
    private String toolError;
    
    public static StreamEvent content(String content) {
        return StreamEvent.builder()
                .type("content")
                .content(content)
                .done(false)
                .build();
    }
    
    public static StreamEvent thinking(String content) {
        return StreamEvent.builder()
                .type("thinking")
                .content(content)
                .done(false)
                .build();
    }
    
    public static StreamEvent toolCall(String toolName, String toolId) {
        return StreamEvent.builder()
                .type("tool_call")
                .toolName(toolName)
                .toolId(toolId)
                .done(false)
                .build();
    }
    
    public static StreamEvent toolResult(String toolName, String toolId, boolean success, String result, String error) {
        return StreamEvent.builder()
                .type("tool_result")
                .toolName(toolName)
                .toolId(toolId)
                .toolSuccess(success)
                .content(result)
                .toolError(error)
                .done(false)
                .build();
    }
    
    public static StreamEvent done(MessageDTO message) {
        return StreamEvent.builder()
                .type("done")
                .content("")
                .done(true)
                .message(message)
                .build();
    }
    
    public static StreamEvent error(String errorMessage) {
        return StreamEvent.builder()
                .type("error")
                .content(errorMessage)
                .done(true)
                .build();
    }
}
