package com.lingobot.learning.chat.service.impl;

import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.conversation.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.entity.ConversationLearningData;
import com.lingobot.learning.conversation.repository.ConversationLearningDataRepository;
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
    private final ConversationLearningDataRepository learningDataRepository;
    private final VocabularyCardRepository vocabularyCardRepository;

    private static final int MAX_CHARACTERS_THRESHOLD = 5000;
    private static final int WORD_CARD_THRESHOLD = 10;

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

        ConversationLearningData learningData = learningDataRepository.findByConversationId(conversationId).orElse(null);
        boolean vocabularyMode = learningData != null && "vocabulary".equalsIgnoreCase(learningData.getLearningMode());

        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int currentCharacters = calculateTotalCharacters(messages);
        int currentTokens = calculateTotalTokens(messages, learningData);

        long wordCardsTotal = vocabularyCardRepository.countActiveCardsByConversationId(conversationId);
        long completedCardsCount = vocabularyCardRepository.countCompletedCardsByConversationId(conversationId);

        boolean shouldCompact = currentCharacters >= MAX_CHARACTERS_THRESHOLD;

        String compactReason = null;
        if (shouldCompact) {
            compactReason = String.format("对话内容已达 %d 字符，超过阈值 %d 字符，建议压缩以释放空间",
                    currentCharacters, MAX_CHARACTERS_THRESHOLD);
        }

        int compactedCount = learningData != null && learningData.getVocabularyCompactedCardCount() != null
                ? learningData.getVocabularyCompactedCardCount() : 0;

        return ContextStatusDTO.builder()
                .currentTokens(currentTokens)
                .maxTokens(MAX_CHARACTERS_THRESHOLD * 2)
                .tokenRatio(Math.min((double) currentCharacters / MAX_CHARACTERS_THRESHOLD, 1.0))
                .wordCardsTotal((int) wordCardsTotal)
                .wordCardsCompleted((int) completedCardsCount)
                .wordCardsSinceCompact((int) completedCardsCount)
                .wordCardThreshold(WORD_CARD_THRESHOLD)
                .shouldCompact(shouldCompact)
                .compactReason(compactReason)
                .compactedCount(compactedCount)
                .build();
    }

    private int calculateTotalCharacters(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            if (message.getContent() != null) {
                total += message.getContent().length();
            }
        }
        log.debug("计算对话字符总量: total={}", total);
        return total;
    }

    private int calculateTotalTokens(List<Message> messages, ConversationLearningData learningData) {
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
        if (learningData != null && learningData.getTotalTokensEstimate() != null) {
            totalTokens += learningData.getTotalTokensEstimate().intValue();
        }

        log.debug("计算对话Token总量: total={}, prompt={}, completion={}",
                totalTokens, promptTokens, completionTokens);

        return totalTokens;
    }
}
