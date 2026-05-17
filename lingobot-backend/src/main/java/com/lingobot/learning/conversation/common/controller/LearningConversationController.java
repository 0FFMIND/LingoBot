package com.lingobot.learning.conversation.common.controller;

import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.learning.conversation.common.dto.CreateLearningConversationRequest;
import com.lingobot.learning.conversation.common.dto.LearningConversationDTO;
import com.lingobot.learning.conversation.common.service.LearningConversationService;
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
public class LearningConversationController {

    private final LearningConversationService learningConversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<LearningConversationDTO>> createConversation(
            @RequestBody CreateLearningConversationRequest request) {
        LearningConversationDTO created = learningConversationService.createConversation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("对话创建成功", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LearningConversationDTO>>> getAllConversations() {
        List<LearningConversationDTO> conversations = learningConversationService.getAllConversations();
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponseDTO<LearningConversationDTO>>> getConversationsByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponseDTO<LearningConversationDTO> pageResponse =
                learningConversationService.getConversationsByPage(page, size);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> getCurrentConversation() {
        Optional<LearningConversationDTO> conversation = learningConversationService.getCurrentConversation();
        if (conversation.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(conversation.get()));
        }
        return ResponseEntity.ok(ApiResponse.success("没有当前选中的对话", null));
    }

    @PutMapping("/current")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> setCurrentConversation(
            @RequestBody Map<String, String> request) {
        String publicId = request.get("publicId");
        learningConversationService.setCurrentConversation(publicId);

        if (publicId == null) {
            return ResponseEntity.ok(ApiResponse.success("已清除当前对话", null));
        }

        LearningConversationDTO conversation = learningConversationService.getConversationByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("已设置当前对话", conversation));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> getConversationByPublicId(
            @PathVariable String publicId) {
        LearningConversationDTO conversation = learningConversationService.getConversationByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> updateConversationTitle(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        LearningConversationDTO updated = learningConversationService.updateConversationTitle(publicId, title);
        return ResponseEntity.ok(ApiResponse.success("对话标题更新成功", updated));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String publicId) {
        learningConversationService.deleteConversation(publicId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{publicId}/learning-mode")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> updateLearningMode(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        LearningConversationDTO updated =
                learningConversationService.updateLearningMode(publicId, request.get("learningMode"));
        return ResponseEntity.ok(ApiResponse.success("学习模式更新成功", updated));
    }

    @PutMapping("/{publicId}/vocabulary-intent")
    public ResponseEntity<ApiResponse<LearningConversationDTO>> updateVocabularyIntent(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        LearningConversationDTO updated =
                learningConversationService.updateVocabularyIntent(publicId, request.get("vocabularyIntent"));
        return ResponseEntity.ok(ApiResponse.success("词汇学习意图更新成功", updated));
    }
}
