package com.lingobot.learning.conversation.controller;

import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.conversation.dto.ConversationViewDTO;
import com.lingobot.learning.conversation.service.ConversationViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationLearningController {

    private final ConversationViewService conversationViewService;

    @PutMapping("/{publicId}/learning-mode")
    public ResponseEntity<ApiResponse<ConversationViewDTO>> updateLearningMode(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        ConversationViewDTO updated = conversationViewService.updateLearningMode(publicId, request.get("learningMode"));
        return ResponseEntity.ok(ApiResponse.success("学习模式更新成功", updated));
    }

    @PutMapping("/{publicId}/vocabulary-intent")
    public ResponseEntity<ApiResponse<ConversationViewDTO>> updateVocabularyIntent(
            @PathVariable String publicId,
            @RequestBody Map<String, String> request) {
        ConversationViewDTO updated = conversationViewService.updateVocabularyIntent(publicId, request.get("vocabularyIntent"));
        return ResponseEntity.ok(ApiResponse.success("词汇学习意图更新成功", updated));
    }
}
