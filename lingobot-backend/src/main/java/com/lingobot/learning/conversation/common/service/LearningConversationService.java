package com.lingobot.learning.conversation.common.service;

import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.conversation.common.dto.CreateLearningConversationRequest;
import com.lingobot.learning.conversation.common.dto.LearningConversationDTO;

import java.util.List;
import java.util.Optional;

public interface LearningConversationService {

    LearningConversationDTO createConversation(CreateLearningConversationRequest request);

    LearningConversationDTO getConversationByPublicId(String publicId);

    List<LearningConversationDTO> getAllConversations();

    PageResponseDTO<LearningConversationDTO> getConversationsByPage(int page, int size);

    LearningConversationDTO updateConversationTitle(String publicId, String title);

    void deleteConversation(String publicId);

    Optional<LearningConversationDTO> getCurrentConversation();

    void setCurrentConversation(String publicId);

    LearningConversationDTO updateLearningMode(String publicId, String learningMode);

    LearningConversationDTO updateVocabularyIntent(String publicId, String vocabularyIntent);
}
