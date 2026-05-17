package com.lingobot.core.user.preference.controller;

import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
import com.lingobot.core.user.preference.dto.UpdateUserPreferenceRequest;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户偏好设置控制器。
 *
 * 提供用户偏好设置的查询和更新 REST 接口，
 * 包括整体设置的查询更新和各单项设置的单独更新。
 */
@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    // 用户偏好设置服务
    private final UserPreferenceService userPreferenceService;
    // 认证服务，用于获取当前登录用户
    private final AuthService authService;

    // 获取当前登录用户的偏好设置，不存在则创建默认设置
    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> getCurrentUserPreference() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success("用户未登录", null));
        }
        
        UserPreferenceDTO preference = userPreferenceService.getOrCreatePreference(userId);
        log.info("获取用户偏好设置: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success(preference));
    }

    // 更新当前登录用户的偏好设置（支持批量更新多个字段）
    @PutMapping
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateCurrentUserPreference(
            @Valid @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, request);
        log.info("更新用户偏好设置成功: userId={}, category={}, difficulty={}, vocabularyModel={}",
                userId, updated.getVocabularyCategory(), updated.getVocabularyDifficulty(), 
                updated.getVocabularyModel());
        return ResponseEntity.ok(ApiResponse.success("偏好设置更新成功", updated));
    }

    // 单独更新词汇划分标准
    @PutMapping("/vocabulary/category")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyCategory(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyCategory(request.getVocabularyCategory())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("词汇划分标准更新成功", updated));
    }

    // 单独更新词汇难度级别
    @PutMapping("/vocabulary/difficulty")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyDifficulty(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyDifficulty(request.getVocabularyDifficulty())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("难度级别更新成功", updated));
    }

    // 单独更新词汇学习使用的 AI 模型
    @PutMapping("/vocabulary/model")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateVocabularyModel(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .vocabularyModel(request.getVocabularyModel())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("词汇学习模型更新成功", updated));
    }

    // 单独更新聊天使用的 AI 模型
    @PutMapping("/chat/model")
    public ResponseEntity<ApiResponse<UserPreferenceDTO>> updateChatModel(
            @RequestBody UpdateUserPreferenceRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        UpdateUserPreferenceRequest updateRequest = UpdateUserPreferenceRequest.builder()
                .chatModel(request.getChatModel())
                .build();
        
        UserPreferenceDTO updated = userPreferenceService.updatePreference(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("聊天模型更新成功", updated));
    }
}
