package com.lingobot.learning.conversation.vocabulary.service.impl;

import com.lingobot.learning.conversation.vocabulary.dto.VocabularyConversationDataDTO;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.repository.VocabularyConversationDataRepository;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VocabularyConversationDataServiceImpl implements VocabularyConversationDataService {

    private final VocabularyConversationDataRepository repository;

    @Override
    @Transactional
    public VocabularyConversationData createOrUpdate(Long conversationId, VocabularyConversationDataDTO dto) {
        Optional<VocabularyConversationData> existing = repository.findByConversationId(conversationId);

        VocabularyConversationData data;
        if (existing.isPresent()) {
            data = existing.get();
            if (dto.getVocabularyIntent() != null) {
                data.setVocabularyIntent(dto.getVocabularyIntent());
            }
            if (dto.getVocabularyCompactedSummary() != null) {
                data.setVocabularyCompactedSummary(dto.getVocabularyCompactedSummary());
            }
            if (dto.getVocabularyCompactedCardCount() != null) {
                data.setVocabularyCompactedCardCount(dto.getVocabularyCompactedCardCount());
            }
        } else {
            data = VocabularyConversationData.builder()
                    .conversationId(conversationId)
                    .vocabularyIntent(dto.getVocabularyIntent())
                    .vocabularyCompactedSummary(dto.getVocabularyCompactedSummary())
                    .vocabularyCompactedCardCount(dto.getVocabularyCompactedCardCount() != null ? dto.getVocabularyCompactedCardCount() : 0)
                    .build();
        }

        return repository.save(data);
    }

    @Override
    public Optional<VocabularyConversationData> getByConversationId(Long conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    @Transactional
    public VocabularyConversationData updateVocabularyIntent(Long conversationId, String vocabularyIntent) {
        VocabularyConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setVocabularyIntent(vocabularyIntent);
        return repository.save(data);
    }

    @Override
    @Transactional
    public VocabularyConversationData updateCompactedSummary(Long conversationId, String compactedSummary,
                                                             Long lastCompactedCardId, Integer lastCompactedPosition,
                                                             Integer compactedCardCount) {
        VocabularyConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setVocabularyCompactedSummary(compactedSummary);
        data.setVocabularyLastCompactedCardId(lastCompactedCardId);
        data.setVocabularyLastCompactedPosition(lastCompactedPosition);
        data.setVocabularyLastCompactedAt(LocalDateTime.now());
        if (compactedCardCount != null) {
            data.setVocabularyCompactedCardCount(compactedCardCount);
        }
        return repository.save(data);
    }

    @Override
    @Transactional
    public void deleteByConversationId(Long conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
