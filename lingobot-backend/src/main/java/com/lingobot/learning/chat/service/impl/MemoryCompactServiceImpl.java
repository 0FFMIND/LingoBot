package com.lingobot.learning.chat.service.impl;

import com.lingobot.learning.chat.service.MemoryCompactService;
import com.lingobot.learning.conversation.entity.ConversationLearningData;
import com.lingobot.learning.conversation.repository.ConversationLearningDataRepository;
import com.lingobot.learning.memory.vocabulary.VocabularyCompactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompactServiceImpl implements MemoryCompactService {

    private final ConversationLearningDataRepository learningDataRepository;
    private final VocabularyCompactService vocabularyCompactService;

    @Override
    public CompactResult executeCompact(Long conversationId) {
        log.info("开始执行对话压缩，conversationId: {}", conversationId);

        ConversationLearningData learningData = learningDataRepository.findByConversationId(conversationId)
                .orElse(null);
        if (learningData == null) {
            return new CompactResult(false, "对话学习数据不存在", 0, 0);
        }

        String learningMode = learningData.getLearningMode();
        if ("vocabulary".equalsIgnoreCase(learningMode)) {
            log.info("检测到 vocabulary 模式，调用 vocabulary compact 逻辑");
            return executeVocabularyCompact(conversationId);
        }

        log.info("非 vocabulary 模式，暂不支持压缩，learningMode: {}", learningMode);
        return new CompactResult(false, "当前模式暂不支持自动压缩", 0, 0);
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
