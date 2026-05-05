package com.lingobot.conversation.service.impl;

import com.lingobot.auth.service.AuthService;
import com.lingobot.auth.entity.User;
import com.lingobot.auth.repository.UserRepository;
import com.lingobot.common.dto.PageResponseDTO;
import com.lingobot.common.exception.ChatException;
import com.lingobot.conversation.dto.ConversationDTO;
import com.lingobot.conversation.dto.CreateConversationRequest;
import com.lingobot.conversation.dto.MessageDTO;
import com.lingobot.conversation.entity.Conversation;
import com.lingobot.conversation.entity.Message;
import com.lingobot.conversation.repository.ConversationRepository;
import com.lingobot.conversation.repository.MessageRepository;
import com.lingobot.conversation.service.ConversationService;
import com.lingobot.vocabulary.repository.VocabularyCardRepository;
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
    
    @Override
    public ConversationDTO getConversationById(Long id) {
        Long currentUserId = authService.getCurrentUserId();
        
        Conversation conversation;
        if (currentUserId != null) {
            conversation = conversationRepository.findByIdAndUserId(id, currentUserId)
                    .orElseThrow(() -> new ChatException("对话不存在或无权访问: " + id));
        } else {
            conversation = conversationRepository.findById(id)
                    .orElseThrow(() -> new ChatException("对话不存�? " + id));
        }
        
        return toDTO(conversation);
    }
    
    @Override
    public List<ConversationDTO> getAllConversations() {
        Long currentUserId = authService.getCurrentUserId();
        
        List<Conversation> conversations;
        if (currentUserId != null) {
            conversations = conversationRepository.findTop20ByUserIdOrderByUpdatedAtDesc(currentUserId);
            log.info("获取用户对话列表（最�?0条），用户ID: {}, 对话�? {}", currentUserId, conversations.size());
        } else {
            conversations = conversationRepository.findTop20ByOrderByUpdatedAtDesc();
            log.info("获取所有对话列表（最�?0条），对话数: {}", conversations.size());
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
    public ConversationDTO updateConversationTitle(Long id, String title) {
        Long currentUserId = authService.getCurrentUserId();
        
        Conversation conversation;
        if (currentUserId != null) {
            conversation = conversationRepository.findByIdAndUserId(id, currentUserId)
                    .orElseThrow(() -> new ChatException("对话不存在或无权访问: " + id));
        } else {
            conversation = conversationRepository.findById(id)
                    .orElseThrow(() -> new ChatException("对话不存�? " + id));
        }
        
        conversation.setTitle(title);
        Conversation saved = conversationRepository.save(conversation);
        return toDTO(saved);
    }
    
    @Override
    @Transactional
    public ConversationDTO updateConversationLearningMode(Long id, String learningMode) {
        Long currentUserId = authService.getCurrentUserId();
        
        Conversation conversation;
        if (currentUserId != null) {
            conversation = conversationRepository.findByIdAndUserId(id, currentUserId)
                    .orElseThrow(() -> new ChatException("对话不存在或无权访问: " + id));
        } else {
            conversation = conversationRepository.findById(id)
                    .orElseThrow(() -> new ChatException("对话不存�? " + id));
        }
        
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
    public void setCurrentConversation(Long conversationId) {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }
        
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return;
        }
        
        if (conversationId == null) {
            user.setCurrentConversationId(null);
        } else {
            Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, currentUserId)
                    .orElse(null);
            if (conversation == null) {
                throw new ChatException("对话不存在或无权访问: " + conversationId);
            }
            user.setCurrentConversationId(conversationId);
        }
        
        userRepository.save(user);
    }
    
    @Override
    @Transactional
    public void deleteConversation(Long id) {
        Long currentUserId = authService.getCurrentUserId();
        
        if (currentUserId != null) {
            if (!conversationRepository.existsByIdAndUserId(id, currentUserId)) {
                throw new ChatException("对话不存在或无权访问: " + id);
            }
        } else {
            if (!conversationRepository.existsById(id)) {
                throw new ChatException("对话不存�? " + id);
            }
        }
        
        vocabularyCardRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
    }
    
    @Override
    public Conversation getConversationEntityById(Long id) {
        Long currentUserId = authService.getCurrentUserId();
        
        if (currentUserId != null) {
            return conversationRepository.findByIdAndUserId(id, currentUserId)
                    .orElseThrow(() -> new ChatException("对话不存在或无权访问: " + id));
        }
        
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ChatException("对话不存�? " + id));
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
        return addAssistantMessage(conversation, content);
    }
    
    @Override
    @Transactional
    public MessageDTO addAssistantMessage(Conversation conversation, String content) {
        Message message = Message.builder()
                .content(content)
                .role("assistant")
                .build();
        conversation.addMessage(message);
        messageRepository.save(message);
        return toMessageDTO(message);
    }
    
    @Override
    @Transactional
    public void deleteMessage(Long messageId) {
        if (!messageRepository.existsById(messageId)) {
            throw new ChatException("消息不存�? " + messageId);
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
                .build();
    }
    
    private ConversationDTO toDTO(Conversation conversation) {
        int messageCount = messageRepository.countByConversationId(conversation.getId());
        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .learningMode(conversation.getLearningMode())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messageCount(messageCount)
                .build();
    }
}
