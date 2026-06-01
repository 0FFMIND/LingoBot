package com.lingobot.learning.chat.service.impl;

import com.lingobot.learning.chat.service.MemoryCompactService;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.memory.vocabulary.VocabularyCompactResult;
import com.lingobot.learning.memory.vocabulary.VocabularyCompactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompactServiceImpl implements MemoryCompactService {

    private final VocabularyConversationDataService vocabularyConversationDataService;
    private final VocabularyCompactService vocabularyCompactService;

    @Override
    public CompactResult executeCompact(Long conversationId) {
        log.info("开始执行对话压缩，conversationId: {}", conversationId);

        boolean isVocabularyMode = vocabularyConversationDataService.getByConversationId(conversationId).isPresent();
        if (!isVocabularyMode) {
            return new CompactResult(false, "对话学习数据不存在", 0, 0, 0, 0, 0, 0, 0);
        }

        log.info("检测到 vocabulary 模式，调用 vocabulary compact 逻辑");
        return executeVocabularyCompact(conversationId);
    }

    private CompactResult executeVocabularyCompact(Long conversationId) {
        try {
            VocabularyCompactResult result = vocabularyCompactService.executeCompact(conversationId);

            if (result.isExecuted()) {
                return new CompactResult(
                    true,
                    "vocabulary 压缩成功",
                    result.getBeforeTokens(),
                    result.getAfterTokens(),
                    result.getCompactedCardsCount(),
                    result.getCompactedCardsCount(),
                    result.getRecentCardsCount(),
                    result.getTotalCompactedCards(),
                    1
                );
            } else {
                return new CompactResult(false, result.getReason(), 0, 0, 0, 0, 0, 0, 0);
            }
        } catch (Exception e) {
            log.error("Vocabulary compact 执行失败", e);
            return new CompactResult(false, "压缩执行失败: " + e.getMessage(), 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
