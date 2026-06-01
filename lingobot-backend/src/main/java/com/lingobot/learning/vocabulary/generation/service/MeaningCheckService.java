package com.lingobot.learning.vocabulary.generation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.service.ToolService;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryInteractionType;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.prompt.vocabulary.VocabularyInteractionCheckPromptBuilder;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.progress.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 释义检查服务。
 *
 * 异步调用 AI Agent 对用户猜测的单词释义进行正误判断，
 * 并将结果通过原生 SQL 写回词汇卡，避免 JPA 托管实体回写旧状态。
 * 检查完成后同步更新用户词汇本的学习进度，并清除相关 Redis 缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeaningCheckService {

    private final ToolLoopService toolLoopService;
    private final ToolService toolService;
    private final VocabularyInteractionCheckPromptBuilder vocabularyInteractionCheckPromptBuilder;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyService userVocabularyService;
    private final VocabularyMemoryService vocabularyMemoryService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final LlmProperties llmProperties;
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";

    // 异步检查用户猜测的释义是否正确，通过 AI Agent 工具调用完成判断
    @Async("meaningCheckExecutor")
    public void checkUserMeaningAsync(Long conversationId, Long cardId, String userMeaning) {
        try {
            log.info("Starting async meaning check for cardId={}, conversationId={}", cardId, conversationId);

            VocabularyCard card = vocabularyCardRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Vocabulary card not found: " + cardId));
            VocabularyCardRepository.CardLearningContextProjection learningContext = vocabularyCardRepository
                    .findLearningContextByCardId(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Vocabulary card learning context not found: " + cardId));

            String word = card.getWord();
            String correctMeaning = card.getMeaning();
            Long resolvedConversationId = learningContext.getConversationId();
            Long vocabularyWordId = learningContext.getVocabularyWordId();
            Long userId = learningContext.getUserId();

            if (word == null || word.isBlank()) {
                log.warn("No word on vocabulary card id={}, skipping meaning check", cardId);
                return;
            }

            String systemPrompt = vocabularyInteractionCheckPromptBuilder.getMeaningCheckPrompt(
                    word,
                    card.getPhonetic(),
                    card.getPartOfSpeech(),
                    correctMeaning,
                    card.getExample(),
                    card.getExampleTranslation(),
                    userMeaning);

            List<OpenAiChatMessage> messages = List.of(
                    OpenAiChatMessage.createTextMessage("system", systemPrompt));

            List<OpenAiTool> tools = toolService.getOpenAiTool("vocabulary", "check_meaning_accuracy");
            if (tools == null || tools.isEmpty()) {
                log.warn("No vocabulary tools available for meaning check");
                return;
            }

            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    null, messages, tools, llmProperties.getModel());
            persistMeaningCheckResult(cardId, resolvedConversationId, vocabularyWordId, userId, userMeaning, result);
            log.info("Meaning check completed for cardId={}", cardId);
        } catch (Exception e) {
            log.error("Meaning check failed for cardId={}, conversationId={}", cardId, conversationId, e);
        }
    }

    // 解析 AI 工具返回结果并通过原生 SQL 持久化释义检查状态，同步更新用户学习进度
    private void persistMeaningCheckResult(Long cardId, Long conversationId, Long vocabularyWordId, Long userId,
                                           String userMeaning, ToolLoopService.ToolLoopResult result) {
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
            if (userId != null && vocabularyWordId != null) {
                userVocabularyService.updateProgress(userId, vocabularyWordId, isCorrect);
                vocabularyCardRepository.findById(cardId).ifPresent(card ->
                        vocabularyMemoryService.recordInteraction(
                                userId,
                                card,
                                isCorrect ? VocabularyMemoryEventType.CORRECT : VocabularyMemoryEventType.WRONG,
                                userMeaning,
                                feedback,
                                VocabularyMemoryInteractionType.MEANING_CHECK));
            }

            int updatedRows = vocabularyCardRepository.updateMeaningCheckResult(
                    cardId,
                    isCorrect,
                    feedback != null ? feedback : "");
            evictCardAndConversationCache(cardId, conversationId);
            log.info("Persisted meaning check by cardId={}, rows={}, isCorrect={}",
                    cardId, updatedRows, isCorrect);
        } catch (Exception e) {
            log.error("Failed to persist meaning check result for cardId={}", cardId, e);
        }
    }

    // 清除词汇卡及所属对话的 Redis 缓存，确保前端轮询拿到最新状态
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
