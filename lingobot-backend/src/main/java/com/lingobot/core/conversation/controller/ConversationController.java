package com.lingobot.core.conversation.controller;

import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.core.conversation.dto.CreateConversationRequest;
import com.lingobot.core.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    
    private final ConversationService conversationService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationDTO>> createConversation(@RequestBody CreateConversationRequest request) {
        ConversationDTO created = conversationService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("对话创建成功", created));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationDTO>>> getAllConversations() {
        List<ConversationDTO> conversations = conversationService.getAllConversations();
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }
    
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponseDTO<ConversationDTO>>> getConversationsByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponseDTO<ConversationDTO> pageResponse = conversationService.getConversationsByPage(page, size);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }
    
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ConversationDTO>> getCurrentConversation() {
        Optional<ConversationDTO> conversation = conversationService.getCurrentConversation();
        if (conversation.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(conversation.get()));
        }
        return ResponseEntity.ok(ApiResponse.success("没有当前选中的对话", null));
    }
    
    @PutMapping("/current")
    public ResponseEntity<ApiResponse<ConversationDTO>> setCurrentConversation(@RequestBody Map<String, String> request) {
        String publicId = request.get("publicId");
        conversationService.setCurrentConversation(publicId);
        
        if (publicId == null) {
            return ResponseEntity.ok(ApiResponse.success("已清除当前对话", null));
        }

        ConversationDTO conversation = conversationService.getConversationByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("已设置当前对话", conversation));
    }
    
    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<ConversationDTO>> getConversationByPublicId(@PathVariable String publicId) {
        ConversationDTO conversation = conversationService.getConversationByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }
    
    @PutMapping("/{publicId}")
    public ResponseEntity<ApiResponse<ConversationDTO>> updateConversationTitle(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        ConversationDTO updated = conversationService.updateConversationTitle(publicId, title);
        return ResponseEntity.ok(ApiResponse.success("对话标题更新成功", updated));
    }
    
    @PutMapping("/{publicId}/learning-mode")
    public ResponseEntity<ApiResponse<ConversationDTO>> updateConversationLearningMode(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        String learningMode = request.get("learningMode");
        ConversationDTO updated = conversationService.updateConversationLearningMode(publicId, learningMode);
        return ResponseEntity.ok(ApiResponse.success("对话学习模式更新成功", updated));
    }
    
    @PutMapping("/{publicId}/vocabulary-intent")
    public ResponseEntity<ApiResponse<ConversationDTO>> updateVocabularyIntent(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        String vocabularyIntent = request.get("vocabularyIntent");
        ConversationDTO updated = conversationService.updateVocabularyIntent(publicId, vocabularyIntent);
        return ResponseEntity.ok(ApiResponse.success("词汇学习意图更新成功", updated));
    }
    
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String publicId) {
        conversationService.deleteConversation(publicId);
        return ResponseEntity.noContent().build();
    }
}
