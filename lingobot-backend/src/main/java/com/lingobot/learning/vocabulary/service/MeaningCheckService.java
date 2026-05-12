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
 * 释义检查服务。
 *
 * 异步调用 AI Agent 检查用户输入的单词释义是否正确，
 * 并将结果持久化到词汇卡，同时更新用户学习进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeaningCheckService {

    private final ToolLoopService toolLoopService;
    private final McpService mcpService;
    private final SystemPromptService systemPromptService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyService userVocabularyService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // 默认使用的AI模型
    private static final String DEFAULT_MODEL = "qwen";
    // Redis 缓存键前缀 - 单个词汇卡
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    // Redis 缓存键前缀 - 对话的词汇卡列表
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    // Redis 缓存键前缀 - 对话的词汇卡数量
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";

    // 异步检查用户释义（使用 meaningCheckExecutor 线程池）
    @Async("meaningCheckExecutor")
    @Transactional
    public void checkUserMeaningAsync(Long conversationId, Long cardId, String userMeaning) {
        try {
            log.info("Starting async meaning check for cardId={}, conversationId={}", cardId, conversationId);

            VocabularyCard card = vocabularyCardRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Vocabulary card not found: " + cardId));

            String word = card.getWord();
            String correctMeaning = card.getMeaning();
            if (word == null || word.isBlank()) {
                log.warn("No word on vocabulary card id={}, skipping meaning check", cardId);
                return;
            }

            String systemPrompt = systemPromptService.getSystemPrompt("vocabulary");

            List<OpenAiChatMessage> messages = new ArrayList<>();
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            messages.add(OpenAiChatMessage.createTextMessage("user", String.format(
                    "[intent:check_meaning][current_word:%s][user_meaning:%s]%nCorrect meaning: %s",
                    word,
                    userMeaning,
                    correctMeaning != null ? correctMeaning : "unknown"
            )));

            List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary");
            if (tools == null || tools.isEmpty()) {
                log.warn("No vocabulary tools available for meaning check");
                return;
            }

            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    conversationId, messages, tools, DEFAULT_MODEL);
            persistMeaningCheckResult(card, result);
            log.info("Meaning check completed for cardId={}", cardId);
        } catch (Exception e) {
            log.error("Meaning check failed for cardId={}, conversationId={}", cardId, conversationId, e);
        }
    }

    // 持久化释义检查结果到词汇卡，并更新用户学习进度
    private void persistMeaningCheckResult(VocabularyCard card, ToolLoopService.ToolLoopResult result) {
        if (result == null || !result.hasToolCalls() || result.getToolResultText() == null) {
            log.warn("Meaning check returned no tool result for cardId={}", card.getId());
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    result.getToolResultText(), new TypeReference<Map<String, Object>>() {});
            Object isCorrectValue = payload.get("is_correct");
            if (!(isCorrectValue instanceof Boolean isCorrect)) {
                log.warn("Meaning check result has no boolean is_correct for cardId={}: {}",
                        card.getId(), result.getToolResultText());
                return;
            }

            String feedback = payload.get("check_feedback") instanceof String value ? value : "";
            card.setMeaningIsCorrect(isCorrect);
            card.setMeaningCheckCompleted(true);
            if (!feedback.isEmpty()) {
                card.setMeaningCheckResult(feedback);
            }

            VocabularyCard saved = vocabularyCardRepository.save(card);
            Long conversationId = saved.getConversation() != null ? saved.getConversation().getId() : null;
            evictCardAndConversationCache(saved.getId(), conversationId);
            if (saved.getVocabularyWordId() != null
                    && saved.getConversation() != null
                    && saved.getConversation().getUser() != null
                    && saved.getConversation().getUser().getId() != null) {
                userVocabularyService.updateProgress(
                        saved.getConversation().getUser().getId(),
                        saved.getVocabularyWordId(),
                        isCorrect);
            }
        } catch (Exception e) {
            log.error("Failed to persist meaning check result for cardId={}", card.getId(), e);
        }
    }

    // 清除词汇卡及其所属对话的Redis缓存
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
