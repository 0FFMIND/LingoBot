package com.lingobot.core.conversation.service;

import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.entity.Message;

import java.util.List;
import java.util.Optional;

public interface ConversationService {
    
    ConversationDTO createConversation(CreateConversationRequest request);
    
    ConversationDTO getConversationById(Long id);
    
    List<ConversationDTO> getAllConversations();
    
    PageResponseDTO<ConversationDTO> getConversationsByPage(int page, int size);
    
    ConversationDTO updateConversationTitle(Long id, String title);
    
    ConversationDTO updateConversationLearningMode(Long id, String learningMode);
    
    void deleteConversation(Long id);
    
    Conversation getConversationEntityById(Long id);
    
    Optional<ConversationDTO> getCurrentConversation();
    
    void setCurrentConversation(Long conversationId);
    
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
    
    void deleteMessage(Long messageId);
    
    void deleteMessagesFromIndex(Long conversationId, int startIndex);
    
    Optional<Message> getLastAssistantMessage(Long conversationId);
    
    Optional<Message> getLastUserMessage(Long conversationId);
    
    List<Message> getLastMessages(Long conversationId, int count);
}
