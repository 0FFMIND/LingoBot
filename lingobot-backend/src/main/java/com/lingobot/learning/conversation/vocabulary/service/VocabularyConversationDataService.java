package com.lingobot.learning.conversation.vocabulary.service;

import com.lingobot.learning.conversation.vocabulary.dto.VocabularyConversationDataDTO;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;

import java.util.Optional;

public interface VocabularyConversationDataService {

    VocabularyConversationData createOrUpdate(Long conversationId, VocabularyConversationDataDTO dto);

    Optional<VocabularyConversationData> getByConversationId(Long conversationId);

    VocabularyConversationData updateVocabularyIntent(Long conversationId, String vocabularyIntent);

    VocabularyConversationData updateCompactedSummary(Long conversationId, String compactedSummary,
                                                      Long lastCompactedCardId, Integer lastCompactedPosition,
                                                      Integer compactedCardCount);

    void deleteByConversationId(Long conversationId);

    VocabularyConversationData updateLastViewedPosition(Long conversationId, Integer position);
}
