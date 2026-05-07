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
 * 词汇学习状态服务 * 使用Redis缓存当前学习的单词信息，用于AI生成造句反馈时的上下文参数 */
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
     * 保存当前学习的单词状态到Redis
     * 当AI调用display_flashcard工具时被调用
     * @param conversationId 对话ID
     * @param word 单词
     * @param phonetic 音标
     * @param partOfSpeech 词性     * @param meaning 释义
     * @param synonyms 同义词列表     * @param vocabularyCategory 词汇标准（cefr/ielts/toefl等）
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
     * 从Redis获取当前学习的单词状态     * @param conversationId 对话ID
     * @return 单词状态Map
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
     * 清除Redis中的单词状态缓存     
     * @param conversationId 对话ID
     */
    public void clearCurrentWord(Long conversationId) {
        String key = VOCABULARY_STATE_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.info("Cleared vocabulary state for conversation {}", conversationId);
    }
    
    /**
     * 生成用于AI提示词的当前单词信息
     * 当AI需要生成造句反馈时，将这些信息注入到System Prompt 中     * @param conversationId 对话ID
     * @return 格式化的提示词文本     
     */
    public String getCurrentWordInfoForPrompt(Long conversationId) {
        Map<String, Object> state = getCurrentWord(conversationId);
        if (state == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 当前学习的单词信息\n");
        sb.append("用户当前正在学习以下单词，请在生成造句反馈时参考这些信息：\n");
        sb.append("- 单词: ").append(state.getOrDefault("word", "")).append("\n");
        sb.append("- 音标: ").append(state.getOrDefault("phonetic", "")).append("\n");
        sb.append("- 词性 ").append(state.getOrDefault("partOfSpeech", "")).append("\n");
        sb.append("- 释义: ").append(state.getOrDefault("meaning", "")).append("\n");
        sb.append("- 同义词: ").append(state.getOrDefault("synonyms", List.of())).append("\n");
        sb.append("- 词汇标准: ").append(state.getOrDefault("vocabularyCategory", "")).append("\n");
        sb.append("- 难度级别: ").append(state.getOrDefault("vocabularyDifficulty", "")).append("\n");
        sb.append("\n**重要提示**：当调用 display_sentence_feedback 工具时，只需要传入 sentence、current_word、feedback、example、exampleTranslation 这几个参数，\n");
        sb.append("**不需要**重复传入 word、phonetic、partOfSpeech、meaning、synonyms、vocabularyCategory、vocabularyDifficulty。\n");
        sb.append("系统会自动从会话状态中获取这些信息。\n");
        
        return sb.toString();
    }
}
