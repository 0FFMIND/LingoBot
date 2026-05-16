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
}
