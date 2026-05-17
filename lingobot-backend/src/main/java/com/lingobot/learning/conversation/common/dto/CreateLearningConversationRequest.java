package com.lingobot.learning.conversation.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLearningConversationRequest {

    private String title;
    private String learningMode;
    private String vocabularyIntent;
}
