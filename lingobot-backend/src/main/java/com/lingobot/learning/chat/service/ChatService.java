package com.lingobot.learning.chat.service;

import com.lingobot.learning.chat.dto.ChatRequest;
import com.lingobot.learning.chat.dto.EditMessageRequest;
import com.lingobot.learning.chat.dto.RetryMessageRequest;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.infrastructure.common.response.PageResponseDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    
    MessageDTO sendMessage(ChatRequest request);
    
    SseEmitter sendMessageStream(ChatRequest request);
    
    PageResponseDTO<MessageDTO> getMessagesByConversationId(Long conversationId, int page, int size);
    
    MessageDTO retryMessage(Long conversationId, Long assistantMessageId);
    
    SseEmitter retryMessageStream(Long conversationId, Long assistantMessageId);
    
    SseEmitter retryMessageStream(RetryMessageRequest request);
    
    MessageDTO editMessage(EditMessageRequest request);
    
    SseEmitter editMessageStream(EditMessageRequest request);
}
