package com.lingobot.learning.chat.service.impl;

import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.conversation.common.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.common.service.LearningConversationAggregateService;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManagerServiceImpl implements ContextManagerService {

    private final LearningConversationAggregateService aggregateService;

    @Override
    public CompactCheckResult checkAndGetReason(Long conversationId) {
        log.debug("检查对话是否需要Compact，conversationId: {}", conversationId);

        ContextStatusDTO status = aggregateService.getContextStatus(conversationId);

        if (Boolean.TRUE.equals(status.getShouldCompact())) {
            return new CompactCheckResult(true, status.getCompactReason());
        }

        return new CompactCheckResult(false, "上下文状态正常");
    }

    @Override
    public ContextStatusDTO getContextStatus(Long conversationId) {
        return aggregateService.getContextStatus(conversationId);
    }
}
