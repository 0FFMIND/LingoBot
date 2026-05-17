package com.lingobot.learning.chat.service.impl;

import com.lingobot.learning.chat.service.MemoryCompactService;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.memory.vocabulary.VocabularyCompactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

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
            return new CompactResult(false, "对话学习数据不存在", 0, 0);
        }

        log.info("检测到 vocabulary 模式，调用 vocabulary compact 逻辑");
        return executeVocabularyCompact(conversationId);
    }

    private CompactResult executeVocabularyCompact(Long conversationId) {
        try {
            Map<String, Object> result = vocabularyCompactService.executeCompact(conversationId);
            
            Boolean executed = (Boolean) result.get("executed");
            if (Boolean.TRUE.equals(executed)) {
                int beforeTokens = result.get("beforeTokens") != null ? ((Number) result.get("beforeTokens")).intValue() : 0;
                int afterTokens = result.get("afterTokens") != null ? ((Number) result.get("afterTokens")).intValue() : 0;
                return new CompactResult(true, "vocabulary 压缩成功", beforeTokens, afterTokens);
            } else {
                String reason = result.get("reason") != null ? result.get("reason").toString() : "压缩未执行";
                return new CompactResult(false, reason, 0, 0);
            }
        } catch (Exception e) {
            log.error("Vocabulary compact 执行失败", e);
            return new CompactResult(false, "压缩执行失败: " + e.getMessage(), 0, 0);
        }
    }
}
