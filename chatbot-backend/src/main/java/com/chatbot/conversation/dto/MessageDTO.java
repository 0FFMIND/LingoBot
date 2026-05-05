package com.lingobot.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    
    private Long id;
    private Long conversationId;
    private String content;
    private String role;
    private LocalDateTime timestamp;
    private String messageType;
    private String audioData;
    private String audioFormat;
    private Integer audioDuration;
    private String imageData;
    private String imageFormat;
    
    public boolean isAudioMessage() {
        return "audio".equals(messageType);
    }
    
    public boolean isImageMessage() {
        return "image".equals(messageType);
    }
}
