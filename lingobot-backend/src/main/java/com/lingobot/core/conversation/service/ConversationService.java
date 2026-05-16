package com.lingobot.core.conversation.service;

import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;

import java.util.List;
import java.util.Optional;

public interface ConversationService {
    
    ConversationDTO createConversation(CreateConversationRequest request);
    
    ConversationDTO getConversationByPublicId(String publicId);
    
    List<ConversationDTO> getAllConversations();
    
    PageResponseDTO<ConversationDTO> getConversationsByPage(int page, int size);
    
    ConversationDTO updateConversationTitle(String publicId, String title);
    
    ConversationDTO updateConversationLearningMode(String publicId, String learningMode);
    
    ConversationDTO updateVocabularyIntent(String publicId, String vocabularyIntent);
    
    void deleteConversation(String publicId);
    
    Conversation getConversationEntityById(Long id);
    
    Optional<ConversationDTO> getCurrentConversation();
    
    void setCurrentConversation(String publicId);
    
    Long resolvePublicIdToId(String publicId);
    
    MessageDTO addUserMessage(Long conversationId, String content);
    
    MessageDTO addUserMessage(Conversation conversation, String content);
    
    MessageDTO addUserMessageWithAudio(Long conversationId, String content, 
                                        String audioData, String audioFormat, Integer audioDuration);
    
    MessageDTO addUserMessageWithAudio(Conversation conversation, String content, 
                                        String audioData, String audioFormat, Integer audioDuration);
    
    MessageDTO addUserMessageWithImage(Long conversationId, String content,
                                        String imageData, String imageFormat);
    
    MessageDTO addUserMessageWithImage(Conversation conversation, String content,
                                        String imageData, String imageFormat);
    
    MessageDTO addAssistantMessage(Long conversationId, String content);
    
    MessageDTO addAssistantMessage(Conversation conversation, String content);
    
    MessageDTO addAssistantMessage(Long conversationId, String content, TokenUsageDTO tokenUsage);
    
    MessageDTO addAssistantMessage(Conversation conversation, String content, TokenUsageDTO tokenUsage);
    
    void deleteMessage(Long messageId);
    
    void deleteMessagesFromIndex(Long conversationId, int startIndex);
    
    Optional<Message> getLastAssistantMessage(Long conversationId);
    
    Optional<Message> getLastUserMessage(Long conversationId);
    
    List<Message> getLastMessages(Long conversationId, int count);
}
