package com.lingobot.learning.conversation.service;

import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.conversation.dto.ConversationViewDTO;
import com.lingobot.learning.conversation.dto.CreateConversationViewRequest;

import java.util.List;
import java.util.Optional;

public interface ConversationViewService {

    ConversationViewDTO createConversation(CreateConversationViewRequest request);

    ConversationViewDTO getConversationByPublicId(String publicId);

    List<ConversationViewDTO> getAllConversations();

    PageResponseDTO<ConversationViewDTO> getConversationsByPage(int page, int size);

    ConversationViewDTO updateConversationTitle(String publicId, String title);

    void deleteConversation(String publicId);

    Optional<ConversationViewDTO> getCurrentConversation();

    void setCurrentConversation(String publicId);

    ConversationViewDTO updateLearningMode(String publicId, String learningMode);

    ConversationViewDTO updateVocabularyIntent(String publicId, String vocabularyIntent);
}
