package com.lingobot.learning.vocabulary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 词汇学习状态服务。
 *
 * 使用 Redis 缓存当前学习的单词信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyStateService {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /** Redis缓存键前缀 */
    private static final String VOCABULARY_STATE_PREFIX = "vocabulary:state:";
    /** 缓存过期时间（小时） */
    private static final long STATE_EXPIRE_HOURS = 24;
    
    /**
     * 保存当前学习的单词状态到 Redis。
     * 当 AI 调用 display_flashcard 工具时被调用。
     * @param conversationId 对话ID
     * @param word 单词
     * @param phonetic 音标
     * @param partOfSpeech 词性
     * @param meaning 释义
     * @param synonyms 同义词列表
     * @param vocabularyCategory 词汇标准（cefr/ielts/toefl等）
     * @param vocabularyDifficulty 难度级别
     */
    public void saveCurrentWord(Long conversationId, String word, String phonetic, String partOfSpeech,
                                  String meaning, List<String> synonyms,
                                  String vocabularyCategory, String vocabularyDifficulty) {
        String key = VOCABULARY_STATE_PREFIX + conversationId;
        
        try {
            Map<String, Object> state = Map.of(
                "word", word != null ? word : "",
                "phonetic", phonetic != null ? phonetic : "",
                "partOfSpeech", partOfSpeech != null ? partOfSpeech : "",
                "meaning", meaning != null ? meaning : "",
                "synonyms", synonyms != null ? synonyms : List.of(),
                "vocabularyCategory", vocabularyCategory != null ? vocabularyCategory : "cefr",
                "vocabularyDifficulty", vocabularyDifficulty != null ? vocabularyDifficulty : "b2"
            );
            
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, STATE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            log.info("Saved vocabulary state for conversation {}: word={}", conversationId, word);
        } catch (Exception e) {
            log.error("Failed to save vocabulary state for conversation {}", conversationId, e);
        }
    }
    
    /**
     * 从 Redis 获取当前学习的单词状态。
     * @param conversationId 对话ID
     * @return 单词状态Map，不存在时返回 null
     */
    public Map<String, Object> getCurrentWord(Long conversationId) {
        String key = VOCABULARY_STATE_PREFIX + conversationId;
        
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            
            Map<String, Object> state = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            log.info("Retrieved vocabulary state for conversation {}: word={}", 
                    conversationId, state.get("word"));
            return state;
        } catch (Exception e) {
            log.error("Failed to get vocabulary state for conversation {}", conversationId, e);
            return null;
        }
    }
    
    /**
     * 清除 Redis 中的单词状态缓存。
     * @param conversationId 对话ID
     */
    public void clearCurrentWord(Long conversationId) {
        String key = VOCABULARY_STATE_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.info("Cleared vocabulary state for conversation {}", conversationId);
    }
    
}
