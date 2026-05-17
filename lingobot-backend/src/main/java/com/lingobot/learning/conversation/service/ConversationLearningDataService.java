package com.lingobot.learning.conversation.service;

import com.lingobot.learning.conversation.dto.ConversationLearningDataDTO;
import com.lingobot.learning.conversation.entity.ConversationLearningData;

import java.util.Optional;

public interface ConversationLearningDataService {

    ConversationLearningData createOrUpdateLearningData(Long conversationId, ConversationLearningDataDTO dto);

    Optional<ConversationLearningData> getLearningDataByConversationId(Long conversationId);

    ConversationLearningData updateLearningMode(Long conversationId, String learningMode);

    ConversationLearningData updateVocabularyIntent(Long conversationId, String vocabularyIntent);

    void deleteLearningDataByConversationId(Long conversationId);
}
