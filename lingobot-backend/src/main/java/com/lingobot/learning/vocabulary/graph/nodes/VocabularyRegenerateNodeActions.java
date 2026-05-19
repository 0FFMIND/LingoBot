package com.lingobot.learning.vocabulary.graph.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.service.ToolService;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryPromptBuilder;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.vocabulary.card.dto.response.WordCardData;
import com.lingobot.learning.vocabulary.graph.VocabularyState;
import com.lingobot.learning.prompt.vocabulary.VocabularyCardGenerationPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 单词卡片重新生成工作流节点动作集合。
 *
 * 为 VocabularyRegenerateGraph 提供各个节点的具体实现逻辑，包括：
 * - MEMORY_RECALL：召回用户历史学习记忆，构建排除词列表
 * - PLANNING：确定生成参数（分类、难度、模型），构建系统提示词
 * - GENERATION：调用AI生成单词卡片
 * - VALIDATION：校验生成的卡片数据完整性和合法性
 *
 * 注意：此工作流仅用于重新生成场景，不负责持久化，持久化由上层服务处理。
 * 每个节点方法返回 AsyncNodeAction，在 CompletableFuture 中异步执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyRegenerateNodeActions {

    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    private static final String DEFAULT_VOCABULARY_DIFFICULTY = "b2";
    private static final String DEFAULT_MODEL = "qwen/qwen3.5-flash-20260224";
    private static final int MAX_RETRIES = 3;

    private final VocabularyMemoryService vocabularyMemoryService;
    private final UserPreferenceService userPreferenceService;
    private final VocabularyCardGenerationPromptBuilder vocabularyCardGenerationPromptBuilder;
    private final VocabularyMemoryPromptBuilder memoryPromptBuilder;
    private final ToolService toolService;
    private final ToolLoopService toolLoopService;
    private final ObjectMapper objectMapper;

    public AsyncNodeAction<VocabularyState> memoryRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing REGEN_MEMORY_RECALL for conversation: {}, user: {}",
                    state.getConversationId(), state.getUserId());

            Map<String, Object> updates = new HashMap<>();

            try {
                var memoryContext = vocabularyMemoryService.retrieveMemory(
                        state.getUserId(),
                        state.getConversationId(),
                        state.getIntent(),
                        null);

                updates.put("memoryContext", memoryContext);

                log.info("Regenerate memory recall completed: total memory items={}",
                        memoryContext.totalMemoryItems());

            } catch (Exception e) {
                log.warn("Regenerate memory recall failed, using empty context", e);
                updates.put("memoryContext", null);
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> planning() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing REGEN_PLANNING for conversation: {}", state.getConversationId());

            if (state.getRegeneratePosition() == null) {
                throw new IllegalStateException("VocabularyRegenerateGraph requires regeneratePosition");
            }

            Map<String, Object> updates = new HashMap<>();

            String category = state.getCategory();
            String difficulty = state.getDifficulty();
            String model = state.getModel();

            if (state.getUserId() != null) {
                UserPreferenceDTO preference = userPreferenceService.getOrCreatePreference(state.getUserId());

                if (category == null || category.isBlank()) {
                    category = preference.getVocabularyCategory();
                }
                if (difficulty == null || difficulty.isBlank()) {
                    difficulty = preference.getVocabularyDifficulty();
                }
                if (model == null || model.isBlank()) {
                    model = preference.getVocabularyFullModel();
                }
            }

            category = (category == null || category.isBlank()) ? DEFAULT_VOCABULARY_CATEGORY : category.toLowerCase();
            difficulty = (difficulty == null || difficulty.isBlank()) ? DEFAULT_VOCABULARY_DIFFICULTY : difficulty.toLowerCase();
            model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.toLowerCase();

            updates.put("category", category);
            updates.put("difficulty", difficulty);
            updates.put("model", model);

            var constraints = VocabularyGenerationConstraints.builder()
                    .category(category)
                    .difficulty(difficulty)
                    .build();
            updates.put("constraints", constraints);

            String systemPrompt = vocabularyCardGenerationPromptBuilder.getRegenerateFlashcardPrompt(
                    category, difficulty,
                    state.getRegeneratePosition() + 1,
                    state.getRegenerateOldWord(),
                    state.getRegenerateOldPartOfSpeech(),
                    state.getRegenerateOldMeaning());

            if (state.getMemoryContext() != null) {
                String memoryPrompt = memoryPromptBuilder.buildPromptContext(state.getMemoryContext());
                if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + memoryPrompt;
                }
            }

            updates.put("systemPrompt", systemPrompt);

            log.info("Regenerate planning completed: category={}, difficulty={}, model={}", category, difficulty, model);
            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> generation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing REGEN_GENERATION for conversation: {}, model: {}, retry: {}",
                    state.getConversationId(), state.getModel(), state.getRetryCount());

            Map<String, Object> updates = new HashMap<>();

            try {
                List<OpenAiChatMessage> messages = new ArrayList<>();
                messages.add(OpenAiChatMessage.createTextMessage("system", state.getSystemPrompt()));

                List<OpenAiTool> tools = toolService.getOpenAiTool("vocabulary", "display_flashcard");

                if (tools == null || tools.isEmpty()) {
                    updates.put("generationError", "No vocabulary tools available");
                    return updates;
                }

                var result = toolLoopService.executeOneTimeToolCall(
                        state.getConversationId(), messages, tools, state.getModel());

                if (result.hasTokenUsage()) {
                    TokenUsageDTO existing = state.getTokenUsage();
                    TokenUsageDTO newUsage = result.getTokenUsage();
                    if (existing == null) {
                        updates.put("tokenUsage", newUsage);
                    } else {
                        TokenUsageDTO merged = TokenUsageDTO.builder()
                                .promptTokens(merge(existing.getPromptTokens(), newUsage.getPromptTokens()))
                                .completionTokens(merge(existing.getCompletionTokens(), newUsage.getCompletionTokens()))
                                .totalTokens(merge(existing.getTotalTokens(), newUsage.getTotalTokens()))
                                .build();
                        updates.put("tokenUsage", merged);
                    }
                }

                if (result.hasToolCalls() && result.getToolResultText() != null) {
                    WordCardData card = parseWordCardData(result.getToolResultText(), state);
                    updates.put("generatedCard", card);
                    log.info("Regenerate generation completed: word={}", card.getWord());
                } else {
                    log.warn("AI did not return valid tool call for regenerate");
                    updates.put("generationError", "Invalid AI response");
                }

            } catch (Exception e) {
                log.error("Regenerate generation failed", e);
                updates.put("generationError", e.getMessage());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> validation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            List<String> errors = new ArrayList<>();

            WordCardData card = state.getGeneratedCard();
            log.info("Executing REGEN_VALIDATION for word: {}", card != null ? card.getWord() : "null");

            if (card == null) {
                errors.add("Generated card is null");
            } else {
                if (card.getWord() == null || card.getWord().isBlank()) {
                    errors.add("Word is empty");
                }
                if (card.getMeaning() == null || card.getMeaning().isBlank()) {
                    errors.add("Meaning is empty");
                }
                if (card.getPartOfSpeech() == null || card.getPartOfSpeech().isBlank()) {
                    errors.add("PartOfSpeech is empty");
                }
                if (card.getExample() == null || card.getExample().isBlank()) {
                    errors.add("Example is empty");
                }
                if (card.getExampleTranslation() == null || card.getExampleTranslation().isBlank()) {
                    errors.add("ExampleTranslation is empty");
                }
            }

            updates.put("validationErrors", errors);
            updates.put("isValid", errors.isEmpty());

            if (errors.isEmpty()) {
                log.info("Regenerate validation passed for word: {}", card.getWord());
            } else {
                log.warn("Regenerate validation failed: {}", errors);
                int retryCount = state.getRetryCount() + 1;
                updates.put("retryCount", retryCount);
            }

            return updates;
        });
    }



    private WordCardData parseWordCardData(String toolResultText, VocabularyState state) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(
                toolResultText, new TypeReference<Map<String, Object>>() {});

        String word = (String) parsed.get("word");

        @SuppressWarnings("unchecked")
        List<String> synonyms = (List<String>) parsed.getOrDefault("synonyms", new ArrayList<String>());

        String category = (String) parsed.get("vocabularyCategory");
        if (category == null || category.isEmpty()) {
            category = state.getCategory();
        }

        String difficulty = (String) parsed.get("vocabularyDifficulty");
        if (difficulty == null || difficulty.isEmpty()) {
            difficulty = state.getDifficulty();
        }

        return WordCardData.builder()
                .word(word)
                .phonetic((String) parsed.get("phonetic"))
                .partOfSpeech((String) parsed.get("partOfSpeech"))
                .meaning((String) parsed.get("meaning"))
                .example((String) parsed.get("example"))
                .exampleTranslation((String) parsed.get("exampleTranslation"))
                .synonyms(synonyms)
                .category(category)
                .difficulty(difficulty)
                .build();
    }

    private Integer merge(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    public boolean shouldRetry(VocabularyState state) {
        return !state.isValid() && state.getRetryCount() < MAX_RETRIES;
    }
}
