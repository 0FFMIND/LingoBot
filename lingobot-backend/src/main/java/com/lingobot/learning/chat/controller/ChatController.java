package com.lingobot.learning.chat.controller;

import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.learning.chat.dto.ChatRequest;
import com.lingobot.learning.chat.dto.EditMessageRequest;
import com.lingobot.learning.chat.dto.RetryMessageRequest;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.core.conversation.dto.MessageDTO;
import com.lingobot.learning.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatService chatService;
    private final ConversationService conversationService;
    
    private Long resolvePublicId(String publicId) {
        return conversationService.resolvePublicIdToId(publicId);
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<MessageDTO>> sendMessage(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        MessageDTO response = chatService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success("消息发送成功", response));
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        return chatService.sendMessageStream(request);
    }
    
    @PostMapping("/onetime")
    public ResponseEntity<ApiResponse<MessageDTO>> sendOnetimeMessage(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getMessageType() == null) {
            request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        }
        MessageDTO response = chatService.sendOnetimeMessage(request);
        return ResponseEntity.ok(ApiResponse.success("一次性消息发送成功", response));
    }
    
    @PostMapping(value = "/onetime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendOnetimeMessageStream(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getMessageType() == null) {
            request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        }
        return chatService.sendOnetimeMessageStream(request);
    }
    
    @PostMapping("/vocabulary")
    public ResponseEntity<ApiResponse<MessageDTO>> sendVocabularyMessage(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getLearningMode() == null) {
            request.setLearningMode("vocabulary");
        }
        MessageDTO response = chatService.sendVocabularyMessage(request);
        return ResponseEntity.ok(ApiResponse.success("词汇消息发送成功", response));
    }
    
    @PostMapping(value = "/vocabulary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendVocabularyMessageStream(@RequestBody ChatRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getLearningMode() == null) {
            request.setLearningMode("vocabulary");
        }
        return chatService.sendVocabularyMessageStream(request);
    }
    
    @GetMapping("/conversations/{conversationPublicId}/messages")
    public ResponseEntity<ApiResponse<List<MessageDTO>>> getMessages(@PathVariable String conversationPublicId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        List<MessageDTO> messages = chatService.getMessagesByConversationId(conversationId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
    
    @PostMapping("/retry/{conversationPublicId}/{assistantMessageId}")
    public ResponseEntity<ApiResponse<MessageDTO>> retryMessage(
            @PathVariable String conversationPublicId,
            @PathVariable Long assistantMessageId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        MessageDTO response = chatService.retryMessage(conversationId, assistantMessageId);
        return ResponseEntity.ok(ApiResponse.success("消息重试成功", response));
    }
    
    @PostMapping(value = "/retry/stream/{conversationPublicId}/{assistantMessageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryMessageStream(
            @PathVariable String conversationPublicId,
            @PathVariable Long assistantMessageId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        return chatService.retryMessageStream(conversationId, assistantMessageId);
    }
    
    @PostMapping(value = "/retry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryMessageStream(@RequestBody RetryMessageRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        return chatService.retryMessageStream(request);
    }
    
    @PostMapping("/edit")
    public ResponseEntity<ApiResponse<MessageDTO>> editMessage(@RequestBody EditMessageRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        MessageDTO response = chatService.editMessage(request);
        return ResponseEntity.ok(ApiResponse.success("消息编辑成功", response));
    }
    
    @PostMapping(value = "/edit/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter editMessageStream(@RequestBody EditMessageRequest request) {
        if (request.getConversationPublicId() != null) {
            request.setConversationId(resolvePublicId(request.getConversationPublicId()));
        }
        return chatService.editMessageStream(request);
    }
}
