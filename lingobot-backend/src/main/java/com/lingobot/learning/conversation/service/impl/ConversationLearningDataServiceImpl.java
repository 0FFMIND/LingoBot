package com.lingobot.learning.conversation.service.impl;

import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.learning.conversation.dto.ConversationLearningDataDTO;
import com.lingobot.learning.conversation.entity.ConversationLearningData;
import com.lingobot.learning.conversation.repository.ConversationLearningDataRepository;
import com.lingobot.learning.conversation.service.ConversationLearningDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationLearningDataServiceImpl implements ConversationLearningDataService {

    private final ConversationLearningDataRepository learningDataRepository;
    private final ConversationRepository conversationRepository;

    @Override
    @Transactional
    public ConversationLearningData createOrUpdateLearningData(Long conversationId, ConversationLearningDataDTO dto) {
        Optional<ConversationLearningData> existing = learningDataRepository.findByConversationId(conversationId);

        ConversationLearningData learningData;
        if (existing.isPresent()) {
            learningData = existing.get();
            if (dto.getLearningMode() != null) {
                learningData.setLearningMode(dto.getLearningMode());
            }
            if (dto.getVocabularyIntent() != null) {
                learningData.setVocabularyIntent(dto.getVocabularyIntent());
            }
        } else {
            learningData = ConversationLearningData.builder()
                    .conversationId(conversationId)
                    .learningMode(dto.getLearningMode() != null ? dto.getLearningMode() : "chat")
                    .vocabularyIntent(dto.getVocabularyIntent())
                    .build();
        }

        return learningDataRepository.save(learningData);
    }

    @Override
    public Optional<ConversationLearningData> getLearningDataByConversationId(Long conversationId) {
        return learningDataRepository.findByConversationId(conversationId);
    }

    @Override
    @Transactional
    public ConversationLearningData updateLearningMode(Long conversationId, String learningMode) {
        ConversationLearningData learningData = learningDataRepository.findByConversationId(conversationId)
                .orElseGet(() -> ConversationLearningData.builder()
                        .conversationId(conversationId)
                        .learningMode(learningMode)
                        .build());
        learningData.setLearningMode(learningMode);

        if ("vocabulary".equals(learningMode)) {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation != null) {
                String currentTitle = conversation.getTitle();
                if (currentTitle == null ||
                        currentTitle.trim().isEmpty() ||
                        currentTitle.startsWith("新对话") ||
                        currentTitle.equals("词汇拓展") ||
                        currentTitle.equals("日常对话")) {
                    LocalDateTime now = LocalDateTime.now();
                    String title = String.format("词汇扩展 %d月%d日%02d时%02d分",
                            now.getMonthValue(), now.getDayOfMonth(),
                            now.getHour(), now.getMinute());
                    conversation.setTitle(title);
                    conversationRepository.save(conversation);
                }
            }
        }

        return learningDataRepository.save(learningData);
    }

    @Override
    @Transactional
    public ConversationLearningData updateVocabularyIntent(Long conversationId, String vocabularyIntent) {
        ConversationLearningData learningData = learningDataRepository.findByConversationId(conversationId)
                .orElseGet(() -> ConversationLearningData.builder()
                        .conversationId(conversationId)
                        .vocabularyIntent(vocabularyIntent)
                        .build());
        learningData.setVocabularyIntent(vocabularyIntent);
        return learningDataRepository.save(learningData);
    }

    @Override
    @Transactional
    public void deleteLearningDataByConversationId(Long conversationId) {
        learningDataRepository.deleteByConversationId(conversationId);
    }
}
