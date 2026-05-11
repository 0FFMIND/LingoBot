package com.lingobot.core.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageDTO {
    
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    
    public boolean isEmpty() {
        return promptTokens == null && completionTokens == null && totalTokens == null;
    }
    
    public Integer getTotal() {
        if (totalTokens != null) {
            return totalTokens;
        }
        int sum = 0;
        if (promptTokens != null) sum += promptTokens;
        if (completionTokens != null) sum += completionTokens;
        return sum > 0 ? sum : null;
    }
}
