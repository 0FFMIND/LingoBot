package com.lingobot.userpreference.controller;

import com.lingobot.auth.service.AuthService;
import com.lingobot.common.response.ApiResponse;
import com.lingobot.userpreference.dto.UpdateUserPreferenceRequest;
import com.lingobot.userpreference.dto.UserPreferenceDTO;
import com.lingobot.userpreference.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> getCurrentUserPreference() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success("用户未登�?, null));
        }
        
        UserPreferenceDTO preference = userPreferenceService.getOrCreatePreference(userId);
        log.info("获取用户偏好设置: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success(preference));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateCurrentUserPreference(
            @Valid @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, request);
        log.info("更新用户偏好设置成功: userId={}, category={}, difficulty={}, vocabularyModel={}",
                userId, updated.getVocabularyCategory(), updated.getVocabularyDifficulty(), 
                updated.getVocabularyModel());
        return ResponseEntity.ok(ApiResponse.success("偏好设置更新成功", updated));
    }

    @PutMapping("/vocabulary/category")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyCategory(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyCategory(request.getVocabularyCategory())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("词汇划分标准更新成功", updated));
    }

    @PutMapping("/vocabulary/difficulty")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyDifficulty(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyDifficulty(request.getVocabularyDifficulty())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("难度级别更新成功", updated));
    }

    @PutMapping("/vocabulary/model")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyModel(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyModel(request.getVocabularyModel())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("词汇学习模型更新成功", updated));
    }

    @PutMapping("/chat/model")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateChatModel(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .chatModel(request.getChatModel())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("聊天模型更新成功", updated));
    }
}
