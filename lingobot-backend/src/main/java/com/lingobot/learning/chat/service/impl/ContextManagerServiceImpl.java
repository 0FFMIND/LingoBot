package com.lingobot.learning.chat.service.impl;

import com.lingobot.core.conversation.dto.ContextStatusDTO;
import com.lingobot.learning.chat.service.ContextManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContextManagerServiceImpl implements ContextManagerService {

    @Override
    public CompactCheckResult checkAndGetReason(Long conversationId) {
        log.debug("检查对话是否需要Compact，conversationId: {}", conversationId);
        return new CompactCheckResult(false, "当前版本暂不启用自动Compact功能");
    }

    @Override
    public ContextStatusDTO getContextStatus(Long conversationId) {
        log.debug("获取对话上下文状态，conversationId: {}", conversationId);
        return ContextStatusDTO.builder()
                .totalMessages(0)
                .compactedMessages(0)
                .compactNeeded(false)
                .reason("当前版本暂不启用上下文管理功能")
                .build();
    }
}
