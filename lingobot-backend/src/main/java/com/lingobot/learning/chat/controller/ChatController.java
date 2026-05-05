package com.lingobot.learning.chat.controller;

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
    
    @PostMapping
    public ResponseEntity<ApiResponse<MessageDTO>> sendMessage(@RequestBody ChatRequest request) {
        MessageDTO response = chatService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success("消息发送成功", response));
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequest request) {
        return chatService.sendMessageStream(request);
    }
    
    @PostMapping("/onetime")
    public ResponseEntity<ApiResponse<MessageDTO>> sendOnetimeMessage(@RequestBody ChatRequest request) {
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getMessageType() == null) {
            request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        }
        MessageDTO response = chatService.sendOnetimeMessage(request);
        return ResponseEntity.ok(ApiResponse.success("一次性消息发送成功", response));
    }
    
    @PostMapping(value = "/onetime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendOnetimeMessageStream(@RequestBody ChatRequest request) {
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getMessageType() == null) {
            request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        }
        return chatService.sendOnetimeMessageStream(request);
    }
    
    @PostMapping("/vocabulary")
    public ResponseEntity<ApiResponse<MessageDTO>> sendVocabularyMessage(@RequestBody ChatRequest request) {
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
        request.setMessageType(ChatRequest.MESSAGE_TYPE_VOCABULARY);
        request.setExecutionMode(ChatRequest.EXECUTION_MODE_ONETIME);
        if (request.getLearningMode() == null) {
            request.setLearningMode("vocabulary");
        }
        return chatService.sendVocabularyMessageStream(request);
    }
    
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<MessageDTO>>> getMessages(@PathVariable Long conversationId) {
        List<MessageDTO> messages = chatService.getMessagesByConversationId(conversationId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
    
    @PostMapping("/retry/{conversationId}/{assistantMessageId}")
    public ResponseEntity<ApiResponse<MessageDTO>> retryMessage(
            @PathVariable Long conversationId,
            @PathVariable Long assistantMessageId) {
        MessageDTO response = chatService.retryMessage(conversationId, assistantMessageId);
        return ResponseEntity.ok(ApiResponse.success("消息重试成功", response));
    }
    
    @PostMapping(value = "/retry/stream/{conversationId}/{assistantMessageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryMessageStream(
            @PathVariable Long conversationId,
            @PathVariable Long assistantMessageId) {
        return chatService.retryMessageStream(conversationId, assistantMessageId);
    }
    
    @PostMapping(value = "/retry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryMessageStream(@RequestBody RetryMessageRequest request) {
        return chatService.retryMessageStream(request);
    }
    
    @PostMapping("/edit")
    public ResponseEntity<ApiResponse<MessageDTO>> editMessage(@RequestBody EditMessageRequest request) {
        MessageDTO response = chatService.editMessage(request);
        return ResponseEntity.ok(ApiResponse.success("消息编辑成功", response));
    }
    
    @PostMapping(value = "/edit/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter editMessageStream(@RequestBody EditMessageRequest request) {
        return chatService.editMessageStream(request);
    }
}
