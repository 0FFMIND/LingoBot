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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private static final String CACHE_KEY_PREFIX = "user:preference:";
    private static final long CACHE_TTL_HOURS = 24;

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private String getCacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }

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

    @Override
    public UserPreferenceDTO getPreference(Long userId) {
        return getOrCreatePreference(userId);
    }

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
        if (request.getVocabularyModel() != null) {
            preference.setVocabularyModel(request.getVocabularyModel().toLowerCase());
        }
        if (request.getChatModel() != null) {
            preference.setChatModel(request.getChatModel().toLowerCase());
        }

        UserPreference saved = userPreferenceRepository.save(preference);
        UserPreferenceDTO dto = toDTO(saved);
        
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.info("用户偏好设置已更新并缓存: userId={}, category={}, difficulty={}, model={}",
                userId, dto.getVocabularyCategory(), dto.getVocabularyDifficulty(), dto.getVocabularyModel());
        
        return dto;
    }

    @Override
    public void evictCache(Long userId) {
        String cacheKey = getCacheKey(userId);
        redisTemplate.delete(cacheKey);
        log.debug("已清除用户偏好设置缓存 userId={}", userId);
    }

    private UserPreference createDefaultPreference(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在 " + userId));
        
        UserPreference preference = UserPreference.builder()
                .user(user)
                .vocabularyCategory("cefr")
                .vocabularyDifficulty("b2")
                .vocabularyModel("qwen")
                .chatModel("qwen")
                .build();
        
        return userPreferenceRepository.save(preference);
    }

    private UserPreferenceDTO toDTO(UserPreference preference) {
        return UserPreferenceDTO.builder()
                .id(preference.getId())
                .userId(preference.getUser() != null ? preference.getUser().getId() : null)
                .vocabularyCategory(preference.getVocabularyCategory())
                .vocabularyDifficulty(preference.getVocabularyDifficulty())
                .vocabularyModel(preference.getVocabularyModel())
                .chatModel(preference.getChatModel())
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
