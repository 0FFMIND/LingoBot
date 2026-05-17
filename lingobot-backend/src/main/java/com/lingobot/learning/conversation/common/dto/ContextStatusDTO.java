package com.lingobot.learning.conversation.common.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContextStatusDTO {

    private Integer currentTokens;
    private Integer maxTokens;
    private Double tokenRatio;
    private Integer wordCardsTotal;
    private Integer wordCardsCompleted;
    private Integer wordCardsSinceCompact;
    private Integer wordCardThreshold;
    private Boolean shouldCompact;
    private String compactReason;
    private Integer compactedCount;
}
