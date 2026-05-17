package com.lingobot.core.user.preference.service;

import com.lingobot.core.user.preference.dto.UpdateUserPreferenceRequest;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;

/**
 * 用户偏好设置服务接口。
 *
 * 提供用户偏好设置的查询和更新功能，包含词汇学习偏好和聊天模型偏好。
 * 使用 Redis 缓存提高查询性能，避免频繁访问数据库。
 */
public interface UserPreferenceService {
    
    // 获取用户偏好设置，不存在则创建默认设置
    UserPreferenceDTO getOrCreatePreference(Long userId);
    
    // 获取用户偏好设置（等同于 getOrCreatePreference）
    UserPreferenceDTO getPreference(Long userId);
    
    // 更新用户偏好设置，更新后刷新缓存
    UserPreferenceDTO updatePreference(Long userId, UpdateUserPreferenceRequest request);
    
    // 清除指定用户的偏好设置缓存
    void evictCache(Long userId);
}
