package com.lingobot.core.user.preference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户偏好设置传输对象。
 *
 * 用于将 UserPreference 实体转换为 API 响应格式，
 * 隐藏用户关联等敏感信息，仅暴露前端所需字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceDTO {
    
    // 偏好设置 ID
    private Long id;
    // 用户 ID
    private Long userId;
    
    // 词汇划分标准（cefr/ielts/toefl）
    private String vocabularyCategory;
    // 词汇难度级别
    private String vocabularyDifficulty;
    // 词汇学习使用的 AI 模型 provider
    private String vocabularyProvider;
    // 词汇学习使用的 AI 模型名
    private String vocabularyModel;
    // 聊天使用的 AI 模型 provider
    private String chatProvider;
    // 聊天使用的 AI 模型名
    private String chatModel;
    
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
