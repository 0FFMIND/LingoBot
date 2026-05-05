package com.lingobot.chat.service;

import com.lingobot.chat.dto.ChatRequest;
import com.lingobot.chat.dto.EditMessageRequest;
import com.lingobot.chat.dto.RetryMessageRequest;
import com.lingobot.conversation.dto.MessageDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {
    
    MessageDTO sendMessage(ChatRequest request);
    
    SseEmitter sendMessageStream(ChatRequest request);
    
    MessageDTO sendOnetimeMessage(ChatRequest request);
    
    SseEmitter sendOnetimeMessageStream(ChatRequest request);
    
    MessageDTO sendVocabularyMessage(ChatRequest request);
    
    SseEmitter sendVocabularyMessageStream(ChatRequest request);
    
    List<MessageDTO> getMessagesByConversationId(Long conversationId);
    
    MessageDTO retryMessage(Long conversationId, Long assistantMessageId);
    
    SseEmitter retryMessageStream(Long conversationId, Long assistantMessageId);
    
    SseEmitter retryMessageStream(RetryMessageRequest request);
    
    MessageDTO editMessage(EditMessageRequest request);
    
    SseEmitter editMessageStream(EditMessageRequest request);
}
