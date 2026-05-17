package com.lingobot.learning.conversation.service.impl;

import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.conversation.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.dto.ConversationLearningDataDTO;
import com.lingobot.learning.conversation.dto.ConversationViewDTO;
import com.lingobot.learning.conversation.dto.CreateConversationViewRequest;
import com.lingobot.learning.conversation.entity.ConversationLearningData;
import com.lingobot.learning.conversation.service.ConversationLearningDataService;
import com.lingobot.learning.conversation.service.ConversationViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationViewServiceImpl implements ConversationViewService {

    private final ConversationService conversationService;
    private final ConversationLearningDataService learningDataService;
    private final ContextManagerService contextManagerService;

    @Override
    @Transactional
    public ConversationViewDTO createConversation(CreateConversationViewRequest request) {
        CreateConversationRequest coreRequest = CreateConversationRequest.builder()
                .title(request != null ? request.getTitle() : null)
                .build();
        ConversationDTO conversation = conversationService.createConversation(coreRequest);
        Long conversationId = conversationService.resolvePublicIdToId(conversation.getPublicId());

        if (request != null && (request.getLearningMode() != null || request.getVocabularyIntent() != null)) {
            ConversationLearningDataDTO learningDataDTO = ConversationLearningDataDTO.builder()
                    .learningMode(request.getLearningMode())
                    .vocabularyIntent(request.getVocabularyIntent())
                    .build();
            learningDataService.createOrUpdateLearningData(conversationId, learningDataDTO);
        }

        return enrich(conversation, conversationId);
    }

    @Override
    public ConversationViewDTO getConversationByPublicId(String publicId) {
        ConversationDTO conversation = conversationService.getConversationByPublicId(publicId);
        return enrich(conversation, conversationService.resolvePublicIdToId(publicId));
    }

    @Override
    public List<ConversationViewDTO> getAllConversations() {
        return conversationService.getAllConversations().stream()
                .map(this::enrich)
                .toList();
    }

    @Override
    public PageResponseDTO<ConversationViewDTO> getConversationsByPage(int page, int size) {
        PageResponseDTO<ConversationDTO> corePage = conversationService.getConversationsByPage(page, size);
        List<ConversationViewDTO> content = corePage.getContent().stream()
                .map(this::enrich)
                .toList();
        return PageResponseDTO.of(content, corePage.getPage(), corePage.getSize(), corePage.getTotalElements());
    }

    @Override
    public ConversationViewDTO updateConversationTitle(String publicId, String title) {
        ConversationDTO conversation = conversationService.updateConversationTitle(publicId, title);
        return enrich(conversation, conversationService.resolvePublicIdToId(publicId));
    }

    @Override
    @Transactional
    public void deleteConversation(String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        learningDataService.deleteLearningDataByConversationId(conversationId);
        conversationService.deleteConversation(publicId);
    }

    @Override
    public Optional<ConversationViewDTO> getCurrentConversation() {
        return conversationService.getCurrentConversation().map(this::enrich);
    }

    @Override
    public void setCurrentConversation(String publicId) {
        conversationService.setCurrentConversation(publicId);
    }

    @Override
    @Transactional
    public ConversationViewDTO updateLearningMode(String publicId, String learningMode) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        learningDataService.updateLearningMode(conversationId, learningMode);
        return enrich(conversationService.getConversationByPublicId(publicId), conversationId);
    }

    @Override
    @Transactional
    public ConversationViewDTO updateVocabularyIntent(String publicId, String vocabularyIntent) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        learningDataService.updateVocabularyIntent(conversationId, vocabularyIntent);
        return enrich(conversationService.getConversationByPublicId(publicId), conversationId);
    }

    private ConversationViewDTO enrich(ConversationDTO conversation) {
        return enrich(conversation, conversationService.resolvePublicIdToId(conversation.getPublicId()));
    }

    private ConversationViewDTO enrich(ConversationDTO conversation, Long conversationId) {
        Optional<ConversationLearningData> learningData =
                learningDataService.getLearningDataByConversationId(conversationId);

        ContextStatusDTO contextStatus = null;
        try {
            contextStatus = contextManagerService.getContextStatus(conversationId);
        } catch (Exception e) {
            log.warn("获取会话上下文状态失败，conversationId: {}", conversationId, e);
        }

        return ConversationViewDTO.builder()
                .publicId(conversation.getPublicId())
                .title(conversation.getTitle())
                .learningMode(learningData.map(ConversationLearningData::getLearningMode).orElse("chat"))
                .vocabularyIntent(learningData.map(ConversationLearningData::getVocabularyIntent).orElse(null))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messageCount(conversation.getMessageCount())
                .contextStatus(contextStatus)
                .build();
    }
}
