package com.lingobot.core.conversation.service.impl;

import com.lingobot.core.conversation.dto.ContextStatusDTO;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.repository.MessageRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final ContextManagerService contextManagerService;
    
    @Override
    @Transactional
    public ConversationDTO createConversation(CreateConversationRequest request) {
        Long currentUserId = authService.getCurrentUserId();
        
        Conversation.ConversationBuilder builder = Conversation.builder()
                .title(request.getTitle());
        
        if (request.getLearningMode() != null && !request.getLearningMode().trim().isEmpty()) {
            builder.learningMode(request.getLearningMode());
        }
        
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId).orElse(null);
            if (user != null) {
                builder.user(user);
                log.info("创建对话关联用户: {}", user.getUsername());
            }
        }
        
        Conversation conversation = builder.build();
        Conversation saved = conversationRepository.save(conversation);
        return toDTO(saved);
    }
    
    private Conversation getConversationEntityByPublicId(String publicId) {
        Long currentUserId = authService.getCurrentUserId();
        
        Conversation conversation;
        if (currentUserId != null) {
            conversation = conversationRepository.findByPublicIdAndUserId(publicId, currentUserId)
                    .orElseThrow(() -> ChatException.badRequest("对话不存在或无权访问: " + publicId));
        } else {
            conversation = conversationRepository.findByPublicId(publicId)
                    .orElseThrow(() -> ChatException.badRequest("对话不存在 " + publicId));
        }
        
        return conversation;
    }
    
    @Override
    public Long resolvePublicIdToId(String publicId) {
        return getConversationEntityByPublicId(publicId).getId();
    }
    
    @Override
    public ConversationDTO getConversationByPublicId(String publicId) {
        return toDTO(getConversationEntityByPublicId(publicId));
    }
    
    @Override
    public List<ConversationDTO> getAllConversations() {
        Long currentUserId = authService.getCurrentUserId();
        
        List<Conversation> conversations;
        if (currentUserId != null) {
            conversations = conversationRepository.findTop20ByUserIdOrderByUpdatedAtDesc(currentUserId);
            log.info("获取用户对话列表（最近20条），用户ID: {}, 对话数 {}", currentUserId, conversations.size());
        } else {
            conversations = conversationRepository.findTop20ByOrderByUpdatedAtDesc();
            log.info("获取所有对话列表（最近20条），对话数: {}", conversations.size());
        }
        
        return conversations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public PageResponseDTO<ConversationDTO> getConversationsByPage(int page, int size) {
        Long currentUserId = authService.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        
        Page<Conversation> conversationPage;
        if (currentUserId != null) {
            conversationPage = conversationRepository.findByUserIdOrderByUpdatedAtDesc(currentUserId, pageable);
            log.info("分页获取用户对话列表，用户ID: {}, 页码: {}, 每页大小: {}, 总数: {}", 
                    currentUserId, page, size, conversationPage.getTotalElements());
        } else {
            conversationPage = conversationRepository.findAllByOrderByUpdatedAtDesc(pageable);
            log.info("分页获取所有对话列表，页码: {}, 每页大小: {}, 总数: {}", 
                    page, size, conversationPage.getTotalElements());
        }
        
        List<ConversationDTO> conversationDTOs = conversationPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return PageResponseDTO.of(
                conversationDTOs,
                conversationPage.getNumber(),
                conversationPage.getSize(),
                conversationPage.getTotalElements()
        );
    }
    
    @Override
    @Transactional
    public ConversationDTO updateConversationTitle(String publicId, String title) {
        Conversation conversation = getConversationEntityByPublicId(publicId);
        conversation.setTitle(title);
        Conversation saved = conversationRepository.save(conversation);
        return toDTO(saved);
    }
    
    @Override
    @Transactional
    public ConversationDTO updateConversationLearningMode(String publicId, String learningMode) {
        Conversation conversation = getConversationEntityByPublicId(publicId);
        conversation.setLearningMode(learningMode);
        Conversation saved = conversationRepository.save(conversation);
        return toDTO(saved);
    }
    
    @Override
    public Optional<ConversationDTO> getCurrentConversation() {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return Optional.empty();
        }
        
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null || user.getCurrentConversationId() == null) {
            return Optional.empty();
        }
        
        Conversation conversation = conversationRepository.findByIdAndUserId(
                user.getCurrentConversationId(), currentUserId).orElse(null);
        
        return Optional.ofNullable(conversation).map(this::toDTO);
    }
    
    @Override
    @Transactional
    public void setCurrentConversation(String publicId) {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }
        
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return;
        }
        
        if (publicId == null) {
            user.setCurrentConversationId(null);
        } else {
            Conversation conversation = getConversationEntityByPublicId(publicId);
            user.setCurrentConversationId(conversation.getId());
        }
        
        userRepository.save(user);
    }
    
    @Override
    @Transactional
    public void deleteConversation(String publicId) {
        Conversation conversation = getConversationEntityByPublicId(publicId);
        Long id = conversation.getId();
        vocabularyCardRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
    }
    
    @Override
    public Conversation getConversationEntityById(Long id) {
        Long currentUserId = authService.getCurrentUserId();
        
        if (currentUserId != null) {
            return conversationRepository.findByIdAndUserId(id, currentUserId)
                    .orElseThrow(() -> ChatException.badRequest("对话不存在或无权访问: " + id));
        }
        
        return conversationRepository.findById(id)
                .orElseThrow(() -> ChatException.badRequest("对话不存在 " + id));
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessage(Long conversationId, String content) {
        Conversation conversation = getConversationEntityById(conversationId);
        return addUserMessage(conversation, content);
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessage(Conversation conversation, String content) {
        Message message = Message.builder()
                .content(content)
                .role("user")
                .build();
        conversation.addMessage(message);
        messageRepository.save(message);
        return toMessageDTO(message);
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessageWithAudio(Long conversationId, String content, 
                                               String audioData, String audioFormat, Integer audioDuration) {
        Conversation conversation = getConversationEntityById(conversationId);
        return addUserMessageWithAudio(conversation, content, audioData, audioFormat, audioDuration);
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessageWithAudio(Conversation conversation, String content, 
                                               String audioData, String audioFormat, Integer audioDuration) {
        Message message = Message.builder()
                .content(content != null ? content : "")
                .role("user")
                .messageType(Message.MESSAGE_TYPE_AUDIO)
                .audioData(audioData)
                .audioFormat(audioFormat)
                .audioDuration(audioDuration)
                .build();
        conversation.addMessage(message);
        messageRepository.save(message);
        return toMessageDTO(message);
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessageWithImage(Long conversationId, String content,
                                               String imageData, String imageFormat) {
        Conversation conversation = getConversationEntityById(conversationId);
        return addUserMessageWithImage(conversation, content, imageData, imageFormat);
    }
    
    @Override
    @Transactional
    public MessageDTO addUserMessageWithImage(Conversation conversation, String content,
                                               String imageData, String imageFormat) {
        Message message = Message.builder()
                .content(content != null ? content : "")
                .role("user")
                .messageType(Message.MESSAGE_TYPE_IMAGE)
                .imageData(imageData)
                .imageFormat(imageFormat)
                .build();
        conversation.addMessage(message);
        messageRepository.save(message);
        return toMessageDTO(message);
    }
    
    @Override
    @Transactional
    public MessageDTO addAssistantMessage(Long conversationId, String content) {
        Conversation conversation = getConversationEntityById(conversationId);
        return addAssistantMessage(conversation, content, null);
    }
    
    @Override
    @Transactional
    public MessageDTO addAssistantMessage(Conversation conversation, String content) {
        return addAssistantMessage(conversation, content, null);
    }
    
    @Override
    @Transactional
    public MessageDTO addAssistantMessage(Long conversationId, String content, TokenUsageDTO tokenUsage) {
        Conversation conversation = getConversationEntityById(conversationId);
        return addAssistantMessage(conversation, content, tokenUsage);
    }
    
    @Override
    @Transactional
    public MessageDTO addAssistantMessage(Conversation conversation, String content, TokenUsageDTO tokenUsage) {
        Message.MessageBuilder messageBuilder = Message.builder()
                .content(content)
                .role("assistant");
        
        if (tokenUsage != null) {
            messageBuilder
                    .promptTokens(tokenUsage.getPromptTokens())
                    .completionTokens(tokenUsage.getCompletionTokens())
                    .totalTokens(tokenUsage.getTotalTokens());
        }
        
        Message message = messageBuilder.build();
        conversation.addMessage(message);
        messageRepository.save(message);
        return toMessageDTO(message);
    }
    
    @Override
    @Transactional
    public void deleteMessage(Long messageId) {
        if (!messageRepository.existsById(messageId)) {
            throw ChatException.badRequest("消息不存在: " + messageId);
        }
        messageRepository.deleteMessageById(messageId);
    }
    
    @Override
    @Transactional
    public void deleteMessagesFromIndex(Long conversationId, int startIndex) {
        List<Message> allMessages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        
        if (startIndex >= allMessages.size()) {
            return;
        }
        
        List<Long> messageIdsToDelete = allMessages.stream()
                .skip(startIndex)
                .map(Message::getId)
                .toList();
        
        if (!messageIdsToDelete.isEmpty()) {
            messageRepository.deleteMessagesByIds(messageIdsToDelete);
        }
    }
    
    @Override
    public Optional<Message> getLastAssistantMessage(Long conversationId) {
        return messageRepository.findFirstByConversationIdAndRoleOrderByTimestampDesc(conversationId, "assistant");
    }
    
    @Override
    public Optional<Message> getLastUserMessage(Long conversationId) {
        return messageRepository.findFirstByConversationIdAndRoleOrderByTimestampDesc(conversationId, "user");
    }
    
    @Override
    public List<Message> getLastMessages(Long conversationId, int count) {
        List<Message> messages = messageRepository.findLastMessagesByConversationId(conversationId);
        return messages.stream()
                .limit(count)
                .collect(Collectors.toList());
    }
    
    private MessageDTO toMessageDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .content(message.getContent())
                .role(message.getRole())
                .timestamp(message.getTimestamp())
                .messageType(message.getMessageType())
                .audioData(message.getAudioData())
                .audioFormat(message.getAudioFormat())
                .audioDuration(message.getAudioDuration())
                .imageData(message.getImageData())
                .imageFormat(message.getImageFormat())
                .promptTokens(message.getPromptTokens())
                .completionTokens(message.getCompletionTokens())
                .totalTokens(message.getTotalTokens())
                .build();
    }
    
    private ConversationDTO toDTO(Conversation conversation) {
        int messageCount = messageRepository.countByConversationId(conversation.getId());
        
        ContextStatusDTO contextStatus = null;
        try {
            contextStatus = contextManagerService.getContextStatus(conversation.getId());
        } catch (Exception e) {
            log.warn("获取上下文状态失败，conversationId: {}", conversation.getId(), e);
        }
        
        return ConversationDTO.builder()
                .publicId(conversation.getPublicId())
                .title(conversation.getTitle())
                .learningMode(conversation.getLearningMode())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messageCount(messageCount)
                .contextStatus(contextStatus)
                .build();
    }
}
