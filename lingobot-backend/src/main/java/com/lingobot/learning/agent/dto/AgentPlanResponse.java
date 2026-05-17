package com.lingobot.learning.agent.dto;

import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanResponse {
    
    private MemoryRecallPlan plan;
    private VocabularyMemoryContext memoryContext;
    private Long conversationId;
}
