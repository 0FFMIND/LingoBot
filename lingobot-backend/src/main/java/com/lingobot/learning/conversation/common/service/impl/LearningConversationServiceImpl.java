package com.lingobot.learning.conversation.common.service.impl;

import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.conversation.chat.entity.ChatConversationData;
import com.lingobot.learning.conversation.chat.service.ChatConversationDataService;
import com.lingobot.learning.conversation.common.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.common.dto.CreateLearningConversationRequest;
import com.lingobot.learning.conversation.common.dto.LearningConversationDTO;
import com.lingobot.learning.conversation.common.service.LearningConversationAggregateService;
import com.lingobot.learning.conversation.common.service.LearningConversationService;
import com.lingobot.learning.conversation.vocabulary.dto.VocabularyConversationDataDTO;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningConversationServiceImpl implements LearningConversationService {

    private final ConversationService conversationService;
    private final ChatConversationDataService chatConversationDataService;
    private final VocabularyConversationDataService vocabularyConversationDataService;
    private final ConversationRepository conversationRepository;
    private final LearningConversationAggregateService aggregateService;

    @Override
    @Transactional
    public LearningConversationDTO createConversation(CreateLearningConversationRequest request) {
        CreateConversationRequest coreRequest = CreateConversationRequest.builder()
                .title(request != null ? request.getTitle() : null)
                .build();
        ConversationDTO conversation = conversationService.createConversation(coreRequest);
        Long conversationId = conversationService.resolvePublicIdToId(conversation.getPublicId());

        if (request != null && request.getLearningMode() != null) {
            initializeLearningData(conversationId, request.getLearningMode(), request.getVocabularyIntent());
        }

        return aggregateService.enrich(conversation, conversationId);
    }

    private void initializeLearningData(Long conversationId, String learningMode, String vocabularyIntent) {
        if ("vocabulary".equals(learningMode)) {
            VocabularyConversationDataDTO dto = VocabularyConversationDataDTO.builder()
                    .vocabularyIntent(vocabularyIntent)
                    .build();
            vocabularyConversationDataService.createOrUpdate(conversationId, dto);
            updateTitleForVocabularyMode(conversationId);
        }
    }

    private void updateTitleForVocabularyMode(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            String currentTitle = conversation.getTitle();
            if (currentTitle == null ||
                    currentTitle.trim().isEmpty() ||
                    currentTitle.startsWith("新对话") ||
                    currentTitle.equals("词汇拓展") ||
                    currentTitle.equals("词汇扩展") ||
                    currentTitle.equals("日常对话")) {
                LocalDateTime now = LocalDateTime.now();
                String title = String.format("词汇拓展 %d月%d日%02d时%02d分",
                        now.getMonthValue(), now.getDayOfMonth(),
                        now.getHour(), now.getMinute());
                conversation.setTitle(title);
                conversationRepository.save(conversation);
            }
        }
    }

    @Override
    public LearningConversationDTO getConversationByPublicId(String publicId) {
        ConversationDTO conversation = conversationService.getConversationByPublicId(publicId);
        return aggregateService.enrich(conversation, conversationService.resolvePublicIdToId(publicId));
    }

    @Override
    public List<LearningConversationDTO> getAllConversations() {
        return conversationService.getAllConversations().stream()
                .map(this::enrich)
                .toList();
    }

    @Override
    public PageResponseDTO<LearningConversationDTO> getConversationsByPage(int page, int size) {
        PageResponseDTO<ConversationDTO> corePage = conversationService.getConversationsByPage(page, size);
        List<LearningConversationDTO> content = corePage.getContent().stream()
                .map(this::enrich)
                .toList();
        return PageResponseDTO.of(content, corePage.getPage(), corePage.getSize(), corePage.getTotalElements());
    }

    @Override
    public LearningConversationDTO updateConversationTitle(String publicId, String title) {
        ConversationDTO conversation = conversationService.updateConversationTitle(publicId, title);
        return aggregateService.enrich(conversation, conversationService.resolvePublicIdToId(publicId));
    }

    @Override
    @Transactional
    public void deleteConversation(String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        chatConversationDataService.deleteByConversationId(conversationId);
        vocabularyConversationDataService.deleteByConversationId(conversationId);
        conversationService.deleteConversation(publicId);
    }

    @Override
    public Optional<LearningConversationDTO> getCurrentConversation() {
        return conversationService.getCurrentConversation().map(this::enrich);
    }

    @Override
    public void setCurrentConversation(String publicId) {
        conversationService.setCurrentConversation(publicId);
    }

    @Override
    @Transactional
    public LearningConversationDTO updateLearningMode(String publicId, String learningMode) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        if ("vocabulary".equals(learningMode)) {
            updateTitleForVocabularyMode(conversationId);
        }
        return aggregateService.enrich(conversationService.getConversationByPublicId(publicId), conversationId);
    }

    @Override
    @Transactional
    public LearningConversationDTO updateVocabularyIntent(String publicId, String vocabularyIntent) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        vocabularyConversationDataService.updateVocabularyIntent(conversationId, vocabularyIntent);
        return aggregateService.enrich(conversationService.getConversationByPublicId(publicId), conversationId);
    }

    private LearningConversationDTO enrich(ConversationDTO conversation) {
        return aggregateService.enrich(conversation, conversationService.resolvePublicIdToId(conversation.getPublicId()));
    }
}
