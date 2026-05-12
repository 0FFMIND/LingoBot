package com.lingobot.learning.vocabulary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.tool.service.McpService;
import com.lingobot.learning.mode.service.SystemPromptService;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 句子分析服务。
 *
 * 异步调用 AI Agent 分析用户根据中文例句写的英文句子，
 * 检查：1）是否与中文例句意思匹配；2）是否包含新单词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentenceAnalysisService {

    private final ToolLoopService toolLoopService;
    private final McpService mcpService;
    private final SystemPromptService systemPromptService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyService userVocabularyService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String DEFAULT_MODEL = "qwen";
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";

    /**
     * 异步分析用户的英文句子
     * 检查：1）是否与中文例句意思匹配；2）是否包含新单词
     */
    @Async("meaningCheckExecutor")
    @Transactional
    public void analyzeUserSentenceAsync(Long cardId) {
        try {
            log.info("Starting async sentence analysis for cardId={}", cardId);

            VocabularyCard card = vocabularyCardRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Vocabulary card not found: " + cardId));

            String word = card.getWord();
            String chineseSentence = card.getChineseSentenceForTranslation();
            String userEnglishSentence = card.getUserEnglishSentence();

            if (word == null || word.isBlank()) {
                log.warn("No word on vocabulary card id={}, skipping sentence analysis", cardId);
                return;
            }
            if (chineseSentence == null || chineseSentence.isBlank()) {
                log.warn("No chineseSentenceForTranslation on card id={}, skipping sentence analysis", cardId);
                return;
            }
            if (userEnglishSentence == null || userEnglishSentence.isBlank()) {
                log.warn("No userEnglishSentence on card id={}, skipping sentence analysis", cardId);
                return;
            }

            String systemPrompt = systemPromptService.getSystemPrompt("vocabulary");

            List<OpenAiChatMessage> messages = new ArrayList<>();
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            messages.add(OpenAiChatMessage.createTextMessage("user", String.format(
                    "[intent:analyze_sentence][current_word:%s]%n" +
                    "Chinese sentence: %s%n" +
                    "User's English sentence: %s%n" +
                    "Please analyze: 1) Does the English sentence match the meaning of the Chinese sentence? " +
                    "2) Does the English sentence contain the new word '%s'?%n" +
                    "Give detailed feedback.",
                    word,
                    chineseSentence,
                    userEnglishSentence,
                    word
            )));

            List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary");
            if (tools == null || tools.isEmpty()) {
                log.warn("No vocabulary tools available for sentence analysis");
                return;
            }

            Long conversationId = card.getConversation() != null ? card.getConversation().getId() : null;
            // Capture lazy relation ids before tool execution; native tool updates can detach this entity.
            Long vocabularyWordId = card.getVocabularyWordId();
            Long userId = card.getConversation() != null && card.getConversation().getUser() != null
                    ? card.getConversation().getUser().getId()
                    : null;
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    conversationId, messages, tools, DEFAULT_MODEL);
            persistSentenceAnalysisResult(cardId, conversationId, vocabularyWordId, userId, result);
            log.info("Sentence analysis completed for cardId={}", cardId);
        } catch (Exception e) {
            log.error("Sentence analysis failed for cardId={}", cardId, e);
        }
    }

    /**
     * 持久化句子分析结果
     */
    private void persistSentenceAnalysisResult(Long cardId, Long conversationId, Long vocabularyWordId, Long userId,
                                               ToolLoopService.ToolLoopResult result) {
        if (result == null || !result.hasToolCalls() || result.getToolResultText() == null) {
            log.warn("Sentence analysis returned no tool result for cardId={}", cardId);
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    result.getToolResultText(), new TypeReference<Map<String, Object>>() {});

            Object meaningMatchesValue = payload.get("meaning_matches");
            Object hasNewWordValue = payload.get("has_new_word");
            String feedback = payload.get("feedback") instanceof String value ? value : "";
            String analysis = payload.get("analysis") instanceof String value ? value : "";

            Boolean meaningMatches = meaningMatchesValue instanceof Boolean v ? v : null;
            Boolean hasNewWord = hasNewWordValue instanceof Boolean v ? v : null;

            String finalAnalysis = !feedback.isEmpty() ? feedback : analysis;

            // Keep sentence status updates on the same direct-update path as meaning checks.
            int updatedRows = vocabularyCardRepository.updateSentenceAnalysisResult(
                    cardId,
                    meaningMatches,
                    hasNewWord,
                    finalAnalysis != null ? finalAnalysis : "");
            evictCardAndConversationCache(cardId, conversationId);
            log.info("Persisted sentence analysis by cardId={}, rows={}", cardId, updatedRows);

            if (userId != null && vocabularyWordId != null) {
                boolean overallCorrect = Boolean.TRUE.equals(meaningMatches) && Boolean.TRUE.equals(hasNewWord);
                userVocabularyService.updateProgress(userId, vocabularyWordId, overallCorrect);
            }
        } catch (Exception e) {
            log.error("Failed to persist sentence analysis result for cardId={}", cardId, e);
        }
    }

    private void evictCardAndConversationCache(Long cardId, Long conversationId) {
        if (cardId != null) {
            stringRedisTemplate.delete(CACHE_KEY_CARD + cardId);
        }
        if (conversationId != null) {
            stringRedisTemplate.delete(CACHE_KEY_CARDS_LIST + conversationId);
            stringRedisTemplate.delete(CACHE_KEY_CARDS_COUNT + conversationId);
        }
    }
}
