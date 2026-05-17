package com.lingobot.learning.conversation.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningConversationDTO {

    private String publicId;
    private String title;
    private String learningMode;
    private String vocabularyIntent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int messageCount;
    private ContextStatusDTO contextStatus;
}
