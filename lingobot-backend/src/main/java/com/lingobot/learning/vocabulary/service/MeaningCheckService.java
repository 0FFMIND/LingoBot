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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final String DEFAULT_MODEL = "qwen";
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";

    @Async("meaningCheckExecutor")
    @Transactional
    public void checkUserMeaningAsync(Long conversationId, Long cardId, String userMeaning) {
        try {
            log.info("Starting async meaning check for cardId={}, conversationId={}", cardId, conversationId);

            VocabularyCard card = vocabularyCardRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Vocabulary card not found: " + cardId));

            String word = card.getWord();
            String correctMeaning = card.getMeaning();
            // Capture lazy relation ids before tool execution. The vocabulary tool uses native updates
            // that clear the persistence context, so accessing card.getConversation().getUser() later can fail.
            Long vocabularyWordId = card.getVocabularyWordId();
            Long userId = card.getConversation() != null && card.getConversation().getUser() != null
                    ? card.getConversation().getUser().getId()
                    : null;

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
            persistMeaningCheckResult(cardId, conversationId, vocabularyWordId, userId, result);
            log.info("Meaning check completed for cardId={}", cardId);
        } catch (Exception e) {
            log.error("Meaning check failed for cardId={}, conversationId={}", cardId, conversationId, e);
        }
    }

    private void persistMeaningCheckResult(Long cardId, Long conversationId, Long vocabularyWordId, Long userId,
                                           ToolLoopService.ToolLoopResult result) {
        if (result == null || !result.hasToolCalls() || result.getToolResultText() == null) {
            log.warn("Meaning check returned no tool result for cardId={}", cardId);
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    result.getToolResultText(), new TypeReference<Map<String, Object>>() {});
            Object isCorrectValue = payload.get("is_correct");
            if (!(isCorrectValue instanceof Boolean isCorrect)) {
                log.warn("Meaning check result has no boolean is_correct for cardId={}: {}",
                        cardId, result.getToolResultText());
                return;
            }

            String feedback = payload.get("check_feedback") instanceof String value ? value : "";
            // Write by card id instead of saving the previously loaded entity. Otherwise an old
            // managed VocabularyCard can overwrite meaning_check_completed back to false.
            int updatedRows = vocabularyCardRepository.updateMeaningCheckResult(
                    cardId,
                    isCorrect,
                    feedback != null ? feedback : "");
            evictCardAndConversationCache(cardId, conversationId);
            log.info("Persisted meaning check by cardId={}, rows={}, isCorrect={}",
                    cardId, updatedRows, isCorrect);

            if (userId != null && vocabularyWordId != null) {
                userVocabularyService.updateProgress(userId, vocabularyWordId, isCorrect);
            }
        } catch (Exception e) {
            log.error("Failed to persist meaning check result for cardId={}", cardId, e);
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
