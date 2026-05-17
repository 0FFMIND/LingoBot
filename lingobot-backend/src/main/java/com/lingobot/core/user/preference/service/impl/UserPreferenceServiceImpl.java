package com.lingobot.core.user.preference.service.impl;

import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.preference.dto.UpdateUserPreferenceRequest;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.entity.UserPreference;
import com.lingobot.core.user.preference.repository.UserPreferenceRepository;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 用户偏好设置服务实现类。
 *
 * 实现 UserPreferenceService 接口，提供用户偏好设置的查询和更新功能。
 * 使用 Redis 缓存用户偏好设置，缓存有效期 24 小时，避免频繁访问数据库。
 * 所有读写操作使用 synchronized 保证线程安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    // Redis 缓存键前缀
    private static final String CACHE_KEY_PREFIX = "user:preference:";
    // 缓存有效期（小时）
    private static final long CACHE_TTL_HOURS = 24;

    // 用户偏好设置仓库
    private final UserPreferenceRepository userPreferenceRepository;
    // 用户仓库
    private final UserRepository userRepository;
    // Redis 模板，用于缓存操作
    private final RedisTemplate<String, Object> redisTemplate;
    // 对象映射器，用于缓存数据的序列化和反序列化
    private final ObjectMapper objectMapper;

    // 构建用户偏好设置的 Redis 缓存键
    private String getCacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }

    // 获取用户偏好设置，不存在则创建默认设置（先查缓存，再查数据库）
    @Override
    @Transactional
    public synchronized UserPreferenceDTO getOrCreatePreference(Long userId) {
        String cacheKey = getCacheKey(userId);
        
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("从Redis缓存获取用户偏好设置: userId={}", userId);
            return objectMapper.convertValue(cached, UserPreferenceDTO.class);
        }

        log.debug("Redis缓存未命中，从数据库查询用户偏好设置: userId={}", userId);
        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUserId(userId);
        
        UserPreference preference;
        if (preferenceOpt.isPresent()) {
            preference = preferenceOpt.get();
        } else {
            log.info("用户偏好设置不存在，创建默认设置: userId={}", userId);
            preference = createDefaultPreference(userId);
        }

        UserPreferenceDTO dto = toDTO(preference);
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.debug("用户偏好设置已缓存到Redis: userId={}", userId);
        
        return dto;
    }

    // 获取用户偏好设置（等同于 getOrCreatePreference）
    @Override
    public UserPreferenceDTO getPreference(Long userId) {
        return getOrCreatePreference(userId);
    }

    // 更新用户偏好设置，更新后刷新缓存
    @Override
    @Transactional
    public synchronized UserPreferenceDTO updatePreference(Long userId, UpdateUserPreferenceRequest request) {
        String cacheKey = getCacheKey(userId);
        
        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUserId(userId);
        
        UserPreference preference;
        if (preferenceOpt.isPresent()) {
            preference = preferenceOpt.get();
            log.info("更新用户偏好设置: userId={}", userId);
        } else {
            log.info("用户偏好设置不存在，创建新设置 userId={}", userId);
            preference = createDefaultPreference(userId);
        }

        if (request.getVocabularyCategory() != null) {
            preference.setVocabularyCategory(request.getVocabularyCategory().toLowerCase());
        }
        if (request.getVocabularyDifficulty() != null) {
            preference.setVocabularyDifficulty(request.getVocabularyDifficulty().toLowerCase());
        }
        if (request.getVocabularyProvider() != null) {
            preference.setVocabularyProvider(request.getVocabularyProvider().toLowerCase());
        }
        if (request.getVocabularyModel() != null) {
            preference.setVocabularyModel(request.getVocabularyModel());
        }
        if (request.getChatProvider() != null) {
            preference.setChatProvider(request.getChatProvider().toLowerCase());
        }
        if (request.getChatModel() != null) {
            preference.setChatModel(request.getChatModel());
        }

        UserPreference saved = userPreferenceRepository.save(preference);
        UserPreferenceDTO dto = toDTO(saved);
        
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.info("用户偏好设置已更新并缓存: userId={}, category={}, difficulty={}, model={}",
                userId, dto.getVocabularyCategory(), dto.getVocabularyDifficulty(), dto.getVocabularyModel());
        
        return dto;
    }

    // 清除指定用户的偏好设置缓存
    @Override
    public void evictCache(Long userId) {
        String cacheKey = getCacheKey(userId);
        redisTemplate.delete(cacheKey);
        log.debug("已清除用户偏好设置缓存 userId={}", userId);
    }

    // 创建用户的默认偏好设置
    private UserPreference createDefaultPreference(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在 " + userId));
        
        UserPreference preference = UserPreference.builder()
                .user(user)
                .vocabularyCategory("cefr")
                .vocabularyDifficulty("b2")
                .vocabularyProvider("qwen")
                .vocabularyModel("qwen3.5-flash-20260224")
                .chatProvider("qwen")
                .chatModel("qwen3.5-flash-20260224")
                .build();
        
        return userPreferenceRepository.save(preference);
    }

    // 将 UserPreference 实体转换为 UserPreferenceDTO
    private UserPreferenceDTO toDTO(UserPreference preference) {
        return UserPreferenceDTO.builder()
                .id(preference.getId())
                .userId(preference.getUser() != null ? preference.getUser().getId() : null)
                .vocabularyCategory(preference.getVocabularyCategory())
                .vocabularyDifficulty(preference.getVocabularyDifficulty())
                .vocabularyProvider(preference.getVocabularyProvider())
                .vocabularyModel(preference.getVocabularyModel())
                .chatProvider(preference.getChatProvider())
                .chatModel(preference.getChatModel())
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
