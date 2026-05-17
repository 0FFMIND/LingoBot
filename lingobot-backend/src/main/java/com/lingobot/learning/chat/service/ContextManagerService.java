package com.lingobot.learning.chat.service;

import com.lingobot.learning.conversation.common.dto.ContextStatusDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface ContextManagerService {

    @Getter
    @AllArgsConstructor
    class CompactCheckResult {
        private final boolean needed;
        private final String reason;
    }

    CompactCheckResult checkAndGetReason(Long conversationId);

    ContextStatusDTO getContextStatus(Long conversationId);
}
