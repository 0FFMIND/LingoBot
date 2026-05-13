package com.lingobot.learning.chat.service.impl;

import com.lingobot.core.conversation.dto.ContextStatusDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManagerServiceImpl implements ContextManagerService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final VocabularyCardRepository vocabularyCardRepository;

    private static final int MAX_TOKENS = 128000;
    private static final int VOCABULARY_MAX_TOKENS = 100000;
    private static final int WORD_CARD_THRESHOLD = 10;
    private static final double COMPACT_RATIO_THRESHOLD = 0.7;

    @Override
    public CompactCheckResult checkAndGetReason(Long conversationId) {
        log.debug("检查对话是否需要Compact，conversationId: {}", conversationId);
        
        ContextStatusDTO status = getContextStatus(conversationId);
        
        if (Boolean.TRUE.equals(status.getShouldCompact())) {
            return new CompactCheckResult(true, status.getCompactReason());
        }
        
        return new CompactCheckResult(false, "上下文状态正常");
    }

    @Override
    public ContextStatusDTO getContextStatus(Long conversationId) {
        log.debug("获取对话上下文状态，conversationId: {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        boolean vocabularyMode = conversation != null && "vocabulary".equalsIgnoreCase(conversation.getLearningMode());
        int maxTokens = vocabularyMode ? VOCABULARY_MAX_TOKENS : MAX_TOKENS;
        int currentTokens = calculateTotalTokens(conversationId, conversation);
        long wordCardsTotal = vocabularyCardRepository.countActiveCardsByConversationId(conversationId);
        
        double tokenRatio = (double) currentTokens / maxTokens;
        
        boolean shouldCompact = tokenRatio >= COMPACT_RATIO_THRESHOLD || wordCardsTotal >= WORD_CARD_THRESHOLD;
        
        String compactReason = null;
        if (tokenRatio >= COMPACT_RATIO_THRESHOLD) {
            compactReason = String.format("上下文Token用量已达 %.0f%%，建议压缩以释放空间", tokenRatio * 100);
        } else if (wordCardsTotal >= WORD_CARD_THRESHOLD) {
            compactReason = String.format("已累计 %d 张单词卡片，建议压缩历史消息", wordCardsTotal);
        }

        return ContextStatusDTO.builder()
                .currentTokens(currentTokens)
                .maxTokens(maxTokens)
                .tokenRatio(Math.min(tokenRatio, 1.0))
                .wordCardsTotal((int) wordCardsTotal)
                .wordCardsSinceCompact((int) wordCardsTotal)
                .wordCardThreshold(WORD_CARD_THRESHOLD)
                .shouldCompact(shouldCompact)
                .compactReason(compactReason)
                .compactedCount(0)
                .build();
    }

    private int calculateTotalTokens(Long conversationId, Conversation conversation) {
        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        
        int totalTokens = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        
        for (Message message : messages) {
            if (message.getTotalTokens() != null) {
                totalTokens += message.getTotalTokens();
            } else {
                int messageTokens = 0;
                if (message.getPromptTokens() != null) {
                    promptTokens += message.getPromptTokens();
                    messageTokens += message.getPromptTokens();
                }
                if (message.getCompletionTokens() != null) {
                    completionTokens += message.getCompletionTokens();
                    messageTokens += message.getCompletionTokens();
                }
                totalTokens += messageTokens;
            }
        }
        if (conversation != null && conversation.getTotalTokensEstimate() != null) {
            totalTokens += conversation.getTotalTokensEstimate().intValue();
        }
        
        log.debug("计算对话 {} 的Token总量: total={}, prompt={}, completion={}", 
                conversationId, totalTokens, promptTokens, completionTokens);
        
        return totalTokens;
    }
}
