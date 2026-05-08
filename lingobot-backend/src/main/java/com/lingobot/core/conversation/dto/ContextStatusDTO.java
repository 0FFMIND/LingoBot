package com.lingobot.core.conversation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContextStatusDTO {

    private int totalMessages;
    private int compactedMessages;
    private boolean compactNeeded;
    private String reason;
}
