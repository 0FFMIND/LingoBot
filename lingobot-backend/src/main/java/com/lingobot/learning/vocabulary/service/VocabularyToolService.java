package com.lingobot.learning.vocabulary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 词汇工具业务服务。
 *
 * 封装词汇学习相关的工具业务逻辑，包括单词卡展示、
 * 释义检查、句子分析等核心功能。
 *
 * 作为词汇工具的业务层，被工具适配器调用，
 * 不直接处理工具调用的参数解析和格式转换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyToolService {

    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyStateService vocabularyStateService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyService userVocabularyService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 展示单词卡。
     *
     * 接收单词信息，保存当前学习状态，并返回格式化的展示数据。
     *
     * @param args 单词卡参数，包含 word、phonetic、partOfSpeech 等
     * @param conversationId 对话 ID
     * @return 单词卡展示结果
     */
    public Map<String, Object> displayFlashcard(Map<String, Object> args, Long conversationId) {
        String word = args != null && args.containsKey("word")
                ? (String) args.get("word")
                : null;
        String phonetic = args != null && args.containsKey("phonetic")
                ? (String) args.get("phonetic")
                : "";
        String partOfSpeech = args != null && args.containsKey("partOfSpeech")
                ? (String) args.get("partOfSpeech")
                : "";
        String meaning = args != null && args.containsKey("meaning")
                ? (String) args.get("meaning")
                : "";
        String example = args != null && args.containsKey("example")
                ? (String) args.get("example")
                : "";
        String exampleTranslation = args != null && args.containsKey("exampleTranslation")
                ? (String) args.get("exampleTranslation")
                : "";
        String vocabularyCategory = args != null && args.containsKey("vocabularyCategory")
                ? (String) args.get("vocabularyCategory")
                : "cefr";
        String vocabularyDifficulty = args != null && args.containsKey("vocabularyDifficulty")
                ? (String) args.get("vocabularyDifficulty")
                : "b2";
        List<String> synonyms = new ArrayList<>();
        if (args != null && args.containsKey("synonyms")) {
            Object synonymsObj = args.get("synonyms");
            if (synonymsObj instanceof List) {
                synonyms = (List<String>) synonymsObj;
            }
        }

        if (conversationId != null) {
            vocabularyStateService.saveCurrentWord(
                    conversationId, word, phonetic, partOfSpeech, meaning,
                    synonyms, vocabularyCategory, vocabularyDifficulty
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("action", "display_flashcard");
        result.put("word", word);
        result.put("phonetic", phonetic);
        result.put("partOfSpeech", partOfSpeech);
        result.put("meaning", meaning);
        result.put("example", example);
        result.put("exampleTranslation", exampleTranslation);
        result.put("synonyms", synonyms);
        result.put("vocabularyCategory", vocabularyCategory);
        result.put("vocabularyDifficulty", vocabularyDifficulty);
        result.put("display_mode", "word_only");
        result.put("message", word != null && phonetic != null
                ? String.format("%s [%s]", word, phonetic)
                : word);

        return result;
    }

    /**
     * 批量展示单词卡。
     *
     * 接收多张单词卡数据，保存首张单词的学习状态，并返回格式化的批量展示数据。
     *
     * @param args 批量单词卡参数，包含 cards 数组
     * @param conversationId 对话 ID
     * @return 批量单词卡展示结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> displayFlashcardBatch(Map<String, Object> args, Long conversationId) {
        List<Map<String, Object>> cardsData = args != null && args.containsKey("cards")
                ? (List<Map<String, Object>>) args.get("cards")
                : new ArrayList<>();

        List<Map<String, Object>> processedCards = new ArrayList<>();
        String firstWord = null;
        String firstPhonetic = null;

        for (int i = 0; i < cardsData.size(); i++) {
            Map<String, Object> cardData = cardsData.get(i);
            String word = cardData != null && cardData.containsKey("word")
                    ? (String) cardData.get("word")
                    : null;
            String phonetic = cardData != null && cardData.containsKey("phonetic")
                    ? (String) cardData.get("phonetic")
                    : "";
            String partOfSpeech = cardData != null && cardData.containsKey("partOfSpeech")
                    ? (String) cardData.get("partOfSpeech")
                    : "";
            String meaning = cardData != null && cardData.containsKey("meaning")
                    ? (String) cardData.get("meaning")
                    : "";
            String example = cardData != null && cardData.containsKey("example")
                    ? (String) cardData.get("example")
                    : "";
            String exampleTranslation = cardData != null && cardData.containsKey("exampleTranslation")
                    ? (String) cardData.get("exampleTranslation")
                    : "";
            String vocabularyCategory = cardData != null && cardData.containsKey("vocabularyCategory")
                    ? (String) cardData.get("vocabularyCategory")
                    : "cefr";
            String vocabularyDifficulty = cardData != null && cardData.containsKey("vocabularyDifficulty")
                    ? (String) cardData.get("vocabularyDifficulty")
                    : "b2";

            List<String> synonyms = new ArrayList<>();
            if (cardData != null && cardData.containsKey("synonyms")) {
                Object synonymsObj = cardData.get("synonyms");
                if (synonymsObj instanceof List) {
                    synonyms = (List<String>) synonymsObj;
                }
            }

            if (i == 0) {
                firstWord = word;
                firstPhonetic = phonetic;
                if (conversationId != null) {
                    vocabularyStateService.saveCurrentWord(
                            conversationId, word, phonetic, partOfSpeech, meaning,
                            synonyms, vocabularyCategory, vocabularyDifficulty
                    );
                }
            }

            Map<String, Object> card = new HashMap<>();
            card.put("word", word);
            card.put("phonetic", phonetic);
            card.put("partOfSpeech", partOfSpeech);
            card.put("meaning", meaning);
            card.put("example", example);
            card.put("exampleTranslation", exampleTranslation);
            card.put("synonyms", synonyms);
            card.put("vocabularyCategory", vocabularyCategory);
            card.put("vocabularyDifficulty", vocabularyDifficulty);
            processedCards.add(card);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("action", "display_flashcard_batch");
        result.put("cards", processedCards);
        result.put("display_mode", "batch");
        result.put("message", firstWord != null && firstPhonetic != null
                ? String.format("已生成 %d 张单词卡，首张: %s [%s]", processedCards.size(), firstWord, firstPhonetic)
                : String.format("已生成 %d 张单词卡", processedCards.size()));

        return result;
    }

    /**
     * 检查用户释义准确性。
     *
     * 接收用户输入的释义和 AI 评估结果，持久化到数据库并更新学习进度。
     *
     * @param args 释义检查参数，包含 user_meaning、is_correct、check_feedback 等
     * @param conversationId 对话 ID
     * @return 释义检查结果
     */
    public Map<String, Object> checkMeaningAccuracy(Map<String, Object> args, Long conversationId) {
        String userMeaning = args != null && args.containsKey("user_meaning")
                ? (String) args.get("user_meaning")
                : "";
        Boolean isCorrect = args != null && args.containsKey("is_correct")
                ? (Boolean) args.get("is_correct")
                : null;
        String checkFeedback = args != null && args.containsKey("check_feedback")
                ? (String) args.get("check_feedback")
                : "";
        String targetWord = args != null && args.containsKey("word")
                ? (String) args.get("word")
                : "";

        Map<String, Object> cachedState = null;
        if (conversationId != null) {
            cachedState = vocabularyStateService.getCurrentWord(conversationId);
            log.info("Retrieved cached state for meaning check, conversation {}: {}", conversationId,
                    cachedState != null ? cachedState.get("word") : "null");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("action", "check_meaning_accuracy");
        result.put("user_meaning", userMeaning);
        result.put("is_correct", isCorrect);
        result.put("check_feedback", checkFeedback);
        result.put("display_mode", "meaning_check");

        String resolvedWord = targetWord;
        if (args != null && args.containsKey("word")) {
            result.put("word", args.get("word"));
        } else if (cachedState != null && cachedState.containsKey("word")) {
            resolvedWord = (String) cachedState.get("word");
            result.put("word", resolvedWord);
        }

        if (args != null && args.containsKey("meaning")) {
            result.put("correct_meaning", args.get("meaning"));
        } else if (cachedState != null && cachedState.containsKey("meaning")) {
            result.put("correct_meaning", cachedState.get("meaning"));
        }

        if (conversationId != null && isCorrect != null) {
            try {
                List<VocabularyCard> candidates = vocabularyCardRepository.findIncompleteByConversationId(conversationId);
                final String wordToMatch = resolvedWord;
                Optional<VocabularyCard> target = candidates.stream()
                        .filter(c -> wordToMatch != null && wordToMatch.equalsIgnoreCase(c.getWord()))
                        .findFirst();
                if (!target.isPresent() && !candidates.isEmpty()) {
                    target = Optional.of(candidates.get(0));
                }
                if (target.isPresent()) {
                    VocabularyCard card = target.get();
                    int updatedRows = vocabularyCardRepository.updateMeaningCheckResult(
                            card.getId(), isCorrect, checkFeedback);
                    evictCardAndConversationCache(card.getId(), conversationId);
                    log.info("Persisted meaning check for cardId={}, word={}, isCorrect={}, rows={}",
                            card.getId(), card.getWord(), isCorrect, updatedRows);

                    Long userId = card.getConversation() != null && card.getConversation().getUser() != null
                            ? card.getConversation().getUser().getId()
                            : null;
                    Long vocabularyWordId = card.getVocabularyWordId();
                    if (userId != null && vocabularyWordId != null) {
                        userVocabularyService.updateProgress(userId, vocabularyWordId, isCorrect);
                        log.info("Updated UserVocabulary progress for userId={}, vocabularyWordId={}, isCorrect={}",
                                userId, vocabularyWordId, isCorrect);
                    }
                } else {
                    log.warn("No vocabulary card found to persist meaning check for conversation={}", conversationId);
                }
            } catch (Exception e) {
                log.error("Failed to persist meaning check result", e);
            }
        }

        return result;
    }

    /**
     * 分析用户英文句子。
     *
     * 接收句子分析结果，持久化到数据库并更新学习进度。
     *
     * @param args 句子分析参数，包含 word、meaning_matches、has_new_word、feedback 等
     * @param conversationId 对话 ID
     * @return 句子分析结果
     */
    public Map<String, Object> analyzeSentence(Map<String, Object> args, Long conversationId) {
        String word = args != null && args.containsKey("word") ? (String) args.get("word") : "";
        Boolean meaningMatches = args != null && args.containsKey("meaning_matches")
                ? (Boolean) args.get("meaning_matches") : null;
        Boolean hasNewWord = args != null && args.containsKey("has_new_word")
                ? (Boolean) args.get("has_new_word") : null;
        String feedback = args != null && args.containsKey("feedback") ? (String) args.get("feedback") : "";

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("action", "analyze_sentence");
        result.put("word", word);
        result.put("meaning_matches", meaningMatches);
        result.put("has_new_word", hasNewWord);
        result.put("feedback", feedback);
        result.put("display_mode", "sentence_analysis");

        if (conversationId != null && (meaningMatches != null || hasNewWord != null)) {
            try {
                List<VocabularyCard> candidates =
                        vocabularyCardRepository.findIncompleteByConversationId(conversationId);
                final String wordToMatch = word;
                Optional<VocabularyCard> target =
                        candidates.stream()
                                .filter(c -> wordToMatch != null && wordToMatch.equalsIgnoreCase(c.getWord()))
                                .findFirst();
                if (!target.isPresent() && !candidates.isEmpty()) {
                    target = Optional.of(candidates.get(0));
                }
                if (target.isPresent()) {
                    VocabularyCard card = target.get();
                    int updatedRows = vocabularyCardRepository.updateSentenceAnalysisResult(
                            card.getId(),
                            meaningMatches,
                            hasNewWord,
                            feedback != null ? feedback : "");
                    evictCardAndConversationCache(card.getId(), conversationId);
                    log.info("Persisted sentence analysis for cardId={}, word={}, rows={}",
                            card.getId(), card.getWord(), updatedRows);

                    Long userId = card.getConversation() != null && card.getConversation().getUser() != null
                            ? card.getConversation().getUser().getId()
                            : null;
                    Long vocabularyWordId = card.getVocabularyWordId();
                    if (userId != null && vocabularyWordId != null && meaningMatches != null && hasNewWord != null) {
                        boolean overallCorrect = meaningMatches && hasNewWord;
                        userVocabularyService.updateProgress(userId, vocabularyWordId, overallCorrect);
                        log.info("Updated UserVocabulary progress for userId={}, vocabularyWordId={}, overallCorrect={}",
                                userId, vocabularyWordId, overallCorrect);
                    }
                } else {
                    log.warn("No vocabulary card found to persist sentence analysis for conversation={}", conversationId);
                }
            } catch (Exception e) {
                log.error("Failed to persist sentence analysis result", e);
            }
        }

        return result;
    }

    /**
     * 清除单词卡及其所属对话的所有缓存。
     *
     * 用于更新/删除操作后清除相关缓存，确保数据一致性。
     *
     * @param cardId 单词卡 ID
     * @param conversationId 对话 ID
     */
    private void evictCardAndConversationCache(Long cardId, Long conversationId) {
        try {
            if (cardId != null) {
                String cardKey = CACHE_KEY_CARD + cardId;
                stringRedisTemplate.delete(cardKey);
                log.debug("Evicted card cache: cardId={}", cardId);
            }
            if (conversationId != null) {
                String listKey = CACHE_KEY_CARDS_LIST + conversationId;
                String countKey = CACHE_KEY_CARDS_COUNT + conversationId;
                stringRedisTemplate.delete(listKey);
                stringRedisTemplate.delete(countKey);
                log.debug("Evicted conversation cache: conversationId={}", conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache for cardId={}, conversationId={}", cardId, conversationId, e);
        }
    }
}
