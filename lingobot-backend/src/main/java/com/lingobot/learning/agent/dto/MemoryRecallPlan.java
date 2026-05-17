package com.lingobot.learning.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecallPlan {
    
    private int l1RecentLimit;
    private int l1WrongLimit;
    private int l1RegeneratedLimit;
    private int l2MasteredLimit;
    private int l2ReviewingLimit;
    private int l2LearningLimit;
    private int l2WeakLimit;
    private String reasoning;
}
