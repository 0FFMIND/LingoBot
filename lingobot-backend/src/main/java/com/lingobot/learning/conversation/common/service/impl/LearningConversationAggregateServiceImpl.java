package com.lingobot.learning.conversation.common.service.impl;

import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.learning.conversation.chat.entity.ChatConversationData;
import com.lingobot.learning.conversation.chat.service.ChatConversationDataService;
import com.lingobot.learning.conversation.common.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.common.dto.LearningConversationDTO;
import com.lingobot.learning.conversation.common.service.LearningConversationAggregateService;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningConversationAggregateServiceImpl implements LearningConversationAggregateService {

    private final ChatConversationDataService chatConversationDataService;
    private final VocabularyConversationDataService vocabularyConversationDataService;
    private final MessageRepository messageRepository;
    private final VocabularyCardRepository vocabularyCardRepository;

    private static final int MAX_CHARACTERS_THRESHOLD = 5000;
    private static final int WORD_CARD_THRESHOLD = 10;

    @Override
    public LearningConversationDTO enrich(ConversationDTO conversation, Long conversationId) {
        Optional<ChatConversationData> chatData = chatConversationDataService.getByConversationId(conversationId);
        Optional<VocabularyConversationData> vocabData = vocabularyConversationDataService.getByConversationId(conversationId);

        String learningMode = resolveLearningMode(chatData, vocabData);
        ContextStatusDTO contextStatus = getContextStatus(conversationId);

        return LearningConversationDTO.builder()
                .publicId(conversation.getPublicId())
                .title(conversation.getTitle())
                .learningMode(learningMode)
                .vocabularyIntent(vocabData.map(VocabularyConversationData::getVocabularyIntent).orElse(null))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messageCount(conversation.getMessageCount())
                .contextStatus(contextStatus)
                .build();
    }

    @Override
    public String resolveLearningMode(Optional<ChatConversationData> chatData,
                                      Optional<VocabularyConversationData> vocabData) {
        if (vocabData.isPresent()) {
            return "vocabulary";
        }
        return "chat";
    }

    @Override
    public ContextStatusDTO getContextStatus(Long conversationId) {
        log.debug("获取对话上下文状态，conversationId: {}", conversationId);

        Optional<VocabularyConversationData> vocabData = vocabularyConversationDataService.getByConversationId(conversationId);
        boolean vocabularyMode = vocabData.isPresent();

        List<Message> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int currentCharacters = calculateTotalCharacters(messages);
        int currentTokens = calculateTotalTokens(messages);

        long wordCardsTotal = vocabularyCardRepository.countActiveCardsByConversationId(conversationId);
        long completedCardsCount = vocabularyCardRepository.countCompletedCardsByConversationId(conversationId);

        boolean shouldCompact = currentCharacters >= MAX_CHARACTERS_THRESHOLD;

        String compactReason = null;
        if (shouldCompact) {
            compactReason = String.format("对话内容已达 %d 字符，超过阈值 %d 字符，建议压缩以释放空间",
                    currentCharacters, MAX_CHARACTERS_THRESHOLD);
        }

        int compactedCount = vocabData.map(VocabularyConversationData::getVocabularyCompactedCardCount).orElse(0);

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
        return total;
    }

    private int calculateTotalTokens(List<Message> messages) {
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

        return totalTokens;
    }
}
