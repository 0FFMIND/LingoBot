package com.lingobot.conversation.controller;

import com.lingobot.common.dto.PageResponseDTO;
import com.lingobot.common.response.ApiResponse;
import com.lingobot.conversation.dto.ConversationDTO;
import com.lingobot.conversation.dto.CreateConversationRequest;
import com.lingobot.conversation.service.ConversationService;
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
        return ResponseEntity.ok(ApiResponse.success("没有当前选中的对�?, null));
    }
    
    @PutMapping("/current")
    public ResponseEntity<ApiResponse<ConversationDTO>> setCurrentConversation(@RequestBody Map<String, Long> request) {
        Long conversationId = request.get("conversationId");
        conversationService.setCurrentConversation(conversationId);
        
        if (conversationId == null) {
            return ResponseEntity.ok(ApiResponse.success("已清除当前对�?, null));
        }
        
        ConversationDTO conversation = conversationService.getConversationById(conversationId);
        return ResponseEntity.ok(ApiResponse.success("已设置当前对�?, conversation));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConversationDTO>> getConversationById(@PathVariable Long id) {
        ConversationDTO conversation = conversationService.getConversationById(id);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConversationDTO>> updateConversationTitle(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        ConversationDTO updated = conversationService.updateConversationTitle(id, title);
        return ResponseEntity.ok(ApiResponse.success("对话标题更新成功", updated));
    }
    
    @PutMapping("/{id}/learning-mode")
    public ResponseEntity<ApiResponse<ConversationDTO>> updateConversationLearningMode(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String learningMode = request.get("learningMode");
        ConversationDTO updated = conversationService.updateConversationLearningMode(id, learningMode);
        return ResponseEntity.ok(ApiResponse.success("对话学习模式更新成功", updated));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.ok(ApiResponse.success("对话已删�?, null));
    }
}
