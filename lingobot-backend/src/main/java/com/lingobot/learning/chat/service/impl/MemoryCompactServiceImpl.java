package com.lingobot.learning.chat.service.impl;

import com.lingobot.learning.chat.service.MemoryCompactService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemoryCompactServiceImpl implements MemoryCompactService {

    @Override
    public CompactResult executeCompact(Long conversationId) {
        log.debug("执行对话Compact，conversationId: {}", conversationId);
        return new CompactResult(false, "当前版本暂不启用Compact功能", 0, 0);
    }
}
