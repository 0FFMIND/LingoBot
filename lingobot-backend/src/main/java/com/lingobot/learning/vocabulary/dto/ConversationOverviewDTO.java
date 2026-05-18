package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationOverviewDTO {

    private long activeCount;

    private long revealedCount;

    private long hiddenCount;

    private long completedCount;
}
