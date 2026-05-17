package com.lingobot.learning.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanRequest {
    
    private String conversationPublicId;
    private Long userId;
    private String intent;
    private String userMessage;
    private String currentWord;
}
