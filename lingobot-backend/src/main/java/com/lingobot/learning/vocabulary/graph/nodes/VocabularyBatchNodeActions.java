package com.lingobot.learning.vocabulary.graph.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.lingobot.infrastructure.common.config.ConversationProperties;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.mcp.service.McpService;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryPromptBuilder;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryRecord;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardBatchData;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import com.lingobot.learning.vocabulary.graph.VocabularyBatchState;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.agent.service.VocabularyAgentService;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.prompt.vocabulary.VocabularyCardGenerationPromptBuilder;
import com.lingobot.learning.vocabulary.service.VocabularyWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 批量单词卡片生成工作流节点动作集合。
 *
 * 为 VocabularyBatchGraph 提供各个节点的具体实现逻辑，包括：
 * - MEMORY_RECALL：召回用户历史学习记忆，构建排除词列表和系统提示词
 * - PLANNING：确定生成参数（分类、难度、模型、批量大小）
 * - GENERATION：调用AI批量生成单词卡片
 * - VALIDATION：校验生成的批量卡片数据完整性、合法性和去重
 * - PERSISTENCE：将生成的批量卡片持久化到数据库
 *
 * 每个节点方法返回 AsyncNodeAction，在 CompletableFuture 中异步执行。
 * 与 VocabularyRegenerateNodeActions 不同，该类处理一次性生成多张卡片的场景。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyBatchNodeActions {

    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    private static final String DEFAULT_VOCABULARY_DIFFICULTY = "b2";
    private static final String DEFAULT_MODEL = "qwen/qwen3.5-flash-20260224";
    private static final int MAX_RETRIES = 3;

    private final VocabularyMemoryService vocabularyMemoryService;
    private final ConversationProperties conversationProperties;
    private final UserPreferenceService userPreferenceService;
    private final VocabularyCardGenerationPromptBuilder vocabularyCardGenerationPromptBuilder;
    private final VocabularyMemoryPromptBuilder memoryPromptBuilder;
    private final ToolService toolService;
    private final ToolLoopService toolLoopService;
    private final ConversationRepository conversationRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final VocabularyWordService vocabularyWordService;
    private final UserVocabularyService userVocabularyService;
    private final VocabularyAgentService vocabularyAgentService;
    private final ObjectMapper objectMapper;

    // LIGHTWEIGHT_RECALL 节点：轻量召回用户学习概览和对话统计
    public AsyncNodeAction<VocabularyBatchState> lightweightRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("执行 BATCH_LIGHTWEIGHT_RECALL: conversationId={}, userId={}, intent={}",
                    state.getConversationId(), state.getUserId(), state.getIntent());

            Map<String, Object> updates = new HashMap<>();

            try {
                if (state.getUserId() != null) {
                    var learningOverview = userVocabularyService.getStats(state.getUserId());
                    updates.put("learningOverview", learningOverview);
                    log.info("用户学习概览: 总词数={}, 新词={}, 学习中={}, 复习中={}, 已掌握={}, 待复习={}, 困难词={}",
                            learningOverview.getTotalCount(),
                            learningOverview.getNewCount(),
                            learningOverview.getLearningCount(),
                            learningOverview.getReviewingCount(),
                            learningOverview.getMasteredCount(),
                            learningOverview.getToReviewCount(),
                            learningOverview.getDifficultCount());
                }

                if (state.getConversationId() != null) {
                    var conversationOverview = vocabularyCardRepository.getConversationOverview(state.getConversationId());
                    updates.put("conversationOverview", conversationOverview);
                    log.info("对话概览: 有效卡片={}, 已揭示={}, 未揭示={}, 已完成={}",
                            conversationOverview.getActiveCount(),
                            conversationOverview.getRevealedCount(),
                            conversationOverview.getHiddenCount(),
                            conversationOverview.getCompletedCount());
                }

            } catch (Exception e) {
                log.warn("轻量召回失败", e);
            }

            return updates;
        });
    }

    // AGENT_MEMORY_RECALL 节点：调用 Memory Agent 进行规划和记忆召回
    public AsyncNodeAction<VocabularyBatchState> agentMemoryRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("执行 BATCH_AGENT_MEMORY_RECALL: conversationId={}, userId={}",
                    state.getConversationId(), state.getUserId());

            Map<String, Object> updates = new HashMap<>();

            try {
                // TODO: 调用 VocabularyAgentService 根据 intent 和 lightweight memory 构建查询计划
                // 1. 构建 AgentPlanRequest（包含 intent、learningOverview、conversationOverview）
                // 2. 调用 vocabularyAgentService.generateMemoryPlan() 或 planAndRecall()
                // 3. 将结果 memoryContext、excludedWords、constraints、systemPrompt 放入 updates

                log.info("Agent 记忆召回节点待实现，当前使用默认记忆召回逻辑");

                var memoryContext = vocabularyMemoryService.retrieveMemory(
                        state.getUserId(),
                        state.getConversationId(),
                        state.getIntent(),
                        null);

                updates.put("memoryContext", memoryContext);

                List<String> excludedWords = buildExcludedWords(memoryContext);
                updates.put("excludedWords", excludedWords);

                log.info("批量记忆召回完成: 记忆条目数={}, 排除词数={}",
                        memoryContext.totalMemoryItems(), excludedWords.size());

                String category = state.getCategory();
                String difficulty = state.getDifficulty();
                Integer batchSize = state.getBatchSize();

                var constraints = VocabularyGenerationConstraints.builder()
                        .category(category)
                        .difficulty(difficulty)
                        .excludeWords(excludedWords)
                        .build();
                updates.put("constraints", constraints);

                String systemPrompt = vocabularyCardGenerationPromptBuilder.getBatchFlashcardPrompt(
                        category, difficulty, batchSize, state.getIntent());

                String memoryPrompt = memoryPromptBuilder.buildPromptContext(memoryContext);
                if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + memoryPrompt;
                }

                updates.put("systemPrompt", systemPrompt);

                log.info("记忆召回后系统提示词构建完成，长度={}", systemPrompt.length());

            } catch (Exception e) {
                log.warn("Agent 记忆召回失败，使用空上下文", e);
                updates.put("memoryContext", null);
                updates.put("excludedWords", new ArrayList<String>());
            }

            return updates;
        });
    }

    // MEMORY_RECALL 节点：召回用户记忆，构建排除词列表和系统提示词
    @Deprecated
    public AsyncNodeAction<VocabularyBatchState> memoryRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("执行 BATCH_MEMORY_RECALL: conversationId={}, userId={}, intent={}",
                    state.getConversationId(), state.getUserId(), state.getIntent());

            Map<String, Object> updates = new HashMap<>();

            try {
                var memoryContext = vocabularyMemoryService.retrieveMemory(
                        state.getUserId(),
                        state.getConversationId(),
                        state.getIntent(),
                        null);

                updates.put("memoryContext", memoryContext);

                List<String> excludedWords = buildExcludedWords(memoryContext);
                updates.put("excludedWords", excludedWords);

                log.info("批量记忆召回完成: 记忆条目数={}, 排除词数={}",
                        memoryContext.totalMemoryItems(), excludedWords.size());

                String category = state.getCategory();
                String difficulty = state.getDifficulty();
                Integer batchSize = state.getBatchSize();

                // 构建生成约束条件
                var constraints = VocabularyGenerationConstraints.builder()
                        .category(category)
                        .difficulty(difficulty)
                        .excludeWords(excludedWords)
                        .build();
                updates.put("constraints", constraints);

                // 构建批量生成的系统提示词
                String systemPrompt = vocabularyCardGenerationPromptBuilder.getBatchFlashcardPrompt(
                        category, difficulty, batchSize, state.getIntent());

                // 拼接记忆上下文
                String memoryPrompt = memoryPromptBuilder.buildPromptContext(memoryContext);
                if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + memoryPrompt;
                }

                updates.put("systemPrompt", systemPrompt);

                log.info("记忆召回后系统提示词构建完成，长度={}", systemPrompt.length());

            } catch (Exception e) {
                log.warn("批量记忆召回失败，使用空上下文", e);
                updates.put("memoryContext", null);
                updates.put("excludedWords", new ArrayList<String>());
            }

            return updates;
        });
    }

    // PLANNING 节点：确定生成参数（分类、难度、模型、批量大小）
    public AsyncNodeAction<VocabularyBatchState> planning() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("执行 BATCH_PLANNING: conversationId={}", state.getConversationId());

            Map<String, Object> updates = new HashMap<>();

            String category = state.getCategory();
            String difficulty = state.getDifficulty();
            String model = state.getModel();
            Integer batchSize = state.getBatchSize();

            // 批量大小为空时使用默认值
            if (batchSize == null || batchSize <= 0) {
                batchSize = conversationProperties.getVocabularyDefaultBatchSize();
            }

            // 优先使用用户偏好设置
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

            // 使用默认值兜底
            category = (category == null || category.isBlank()) ? DEFAULT_VOCABULARY_CATEGORY : category.toLowerCase();
            difficulty = (difficulty == null || difficulty.isBlank()) ? DEFAULT_VOCABULARY_DIFFICULTY : difficulty.toLowerCase();
            model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.toLowerCase();

            updates.put("category", category);
            updates.put("difficulty", difficulty);
            updates.put("model", model);
            updates.put("batchSize", batchSize);

            log.info("批量规划完成: category={}, difficulty={}, model={}, batchSize={}, intent={}",
                    category, difficulty, model, batchSize, state.getIntent());
            return updates;
        });
    }

    // GENERATION 节点：调用AI批量生成单词卡片
    public AsyncNodeAction<VocabularyBatchState> generation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("执行 BATCH_GENERATION: conversationId={}, model={}, retry={}, batchSize={}",
                    state.getConversationId(), state.getModel(), state.getRetryCount(), state.getBatchSize());

            Map<String, Object> updates = new HashMap<>();

            try {
                List<OpenAiChatMessage> messages = new ArrayList<>();
                messages.add(OpenAiChatMessage.createTextMessage("system", state.getSystemPrompt()));

                List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary", "display_flashcard_batch");

                if (tools == null || tools.isEmpty()) {
                    updates.put("generationError", "无可用的批量词汇生成工具");
                    return updates;
                }

                var result = toolLoopService.executeOneTimeToolCall(
                        state.getConversationId(), messages, tools, state.getModel());

                // 累加Token使用统计
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

                // 解析AI返回的工具调用结果
                if (result.hasToolCalls() && result.getToolResultText() != null) {
                    WordCardBatchData batch = parseWordCardBatchData(result.getToolResultText(), state);
                    updates.put("generatedBatch", batch);
                    log.info("批量生成完成: 卡片数={}",
                            batch.getCards() != null ? batch.getCards().size() : 0);
                } else {
                    log.warn("批量生成时AI未返回有效的工具调用结果");
                    updates.put("generationError", "AI响应无效");
                }

            } catch (Exception e) {
                log.error("批量生成失败", e);
                updates.put("generationError", e.getMessage());
            }

            return updates;
        });
    }

    // VALIDATION 节点：校验生成的批量卡片数据
    public AsyncNodeAction<VocabularyBatchState> validation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            List<String> errors = new ArrayList<>();

            WordCardBatchData batch = state.getGeneratedBatch();
            log.info("执行 BATCH_VALIDATION: 卡片数={}",
                    batch != null && batch.getCards() != null ? batch.getCards().size() : 0);

            if (batch == null || batch.getCards() == null || batch.getCards().isEmpty()) {
                errors.add("生成的批量卡片为空");
            } else {
                List<String> wordsInBatch = new ArrayList<>();
                for (int i = 0; i < batch.getCards().size(); i++) {
                    WordCardData card = batch.getCards().get(i);
                    if (card == null) {
                        errors.add("索引 " + i + " 处的卡片为空");
                        continue;
                    }
                    // 校验单词必填和去重
                    if (card.getWord() == null || card.getWord().isBlank()) {
                        errors.add("索引 " + i + " 处的卡片: 单词为空");
                    } else {
                        String normalizedWord = card.getWord().toLowerCase().trim();
                        if (wordsInBatch.contains(normalizedWord)) {
                            errors.add("索引 " + i + " 处的卡片: 批量中存在重复单词 '" + card.getWord() + "'");
                        }
                        wordsInBatch.add(normalizedWord);
                    }
                    // 校验其他必填字段
                    if (card.getMeaning() == null || card.getMeaning().isBlank()) {
                        errors.add("索引 " + i + " 处的卡片: 释义为空");
                    }
                    if (card.getPartOfSpeech() == null || card.getPartOfSpeech().isBlank()) {
                        errors.add("索引 " + i + " 处的卡片: 词性为空");
                    }
                    if (card.getExample() == null || card.getExample().isBlank()) {
                        errors.add("索引 " + i + " 处的卡片: 例句为空");
                    }
                    if (card.getExampleTranslation() == null || card.getExampleTranslation().isBlank()) {
                        errors.add("索引 " + i + " 处的卡片: 例句翻译为空");
                    }
                }

                // 校验批量大小是否匹配
                if (batch.getCards().size() != state.getBatchSize()) {
                    log.warn("批量大小不匹配: 期望={}, 实际={}", state.getBatchSize(), batch.getCards().size());
                }
            }

            updates.put("validationErrors", errors);
            updates.put("isValid", errors.isEmpty());

            if (errors.isEmpty()) {
                log.info("批量校验通过");
            } else {
                log.warn("批量校验失败: {}", errors);
                int retryCount = state.getRetryCount() + 1;
                updates.put("retryCount", retryCount);
            }

            return updates;
        });
    }

    // PERSISTENCE 节点：将生成的批量卡片持久化到数据库
    public AsyncNodeAction<VocabularyBatchState> persistence() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            WordCardBatchData batch = state.getGeneratedBatch();

            if (batch == null || batch.getCards() == null) {
                updates.put("persistenceError", "生成的批量卡片为空");
                return updates;
            }

            log.info("执行 BATCH_PERSISTENCE: 卡片数={}", batch.getCards().size());

            try {
                List<VocabularyCardDTO> savedCards = createCardsInternal(state.getConversationId(), batch.getCards(), true);
                updates.put("savedCards", savedCards);
                log.info("批量持久化完成: 已保存 {} 张卡片", savedCards.size());

            } catch (Exception e) {
                log.error("批量持久化失败", e);
                updates.put("persistenceError", e.getMessage());
            }

            return updates;
        });
    }

    // 从记忆上下文中构建排除词列表
    private List<String> buildExcludedWords(VocabularyMemoryContext context) {
        return Stream.of(
                        context.getConversationRecentCards().stream(),
                        context.getRecentWords().stream(),
                        context.getRegeneratedWords().stream()
                )
                .flatMap(s -> s)
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    // 解析AI返回的工具调用结果为批量单词卡片数据
    private WordCardBatchData parseWordCardBatchData(String toolResultText, VocabularyBatchState state) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(
                toolResultText, new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cardsData = (List<Map<String, Object>>) parsed.getOrDefault("cards", new ArrayList<>());

        List<WordCardData> cards = new ArrayList<>();
        for (Map<String, Object> cardData : cardsData) {
            String word = (String) cardData.get("word");

            @SuppressWarnings("unchecked")
            List<String> synonyms = (List<String>) cardData.getOrDefault("synonyms", new ArrayList<String>());

            String category = (String) cardData.get("vocabularyCategory");
            if (category == null || category.isEmpty()) {
                category = state.getCategory();
            }

            String difficulty = (String) cardData.get("vocabularyDifficulty");
            if (difficulty == null || difficulty.isEmpty()) {
                difficulty = state.getDifficulty();
            }

            WordCardData card = WordCardData.builder()
                    .word(word)
                    .phonetic((String) cardData.get("phonetic"))
                    .partOfSpeech((String) cardData.get("partOfSpeech"))
                    .meaning((String) cardData.get("meaning"))
                    .example((String) cardData.get("example"))
                    .exampleTranslation((String) cardData.get("exampleTranslation"))
                    .synonyms(synonyms)
                    .category(category)
                    .difficulty(difficulty)
                    .build();
            cards.add(card);
        }

        return WordCardBatchData.builder()
                .cards(cards)
                .build();
    }

    // 合并两个整数（空值安全）
    private Integer merge(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    // 判断是否需要重试（校验失败且未超过最大重试次数）
    public boolean shouldRetry(VocabularyBatchState state) {
        return !state.isValid() && state.getRetryCount() < MAX_RETRIES;
    }

    // 批量创建单词卡片并持久化到数据库（事务内执行）
    @Transactional
    private List<VocabularyCardDTO> createCardsInternal(Long conversationId, List<WordCardData> cardsData, boolean revealFirst) {
        List<VocabularyCardDTO> savedCards = new ArrayList<>();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("对话不存在: " + conversationId));

        // 获取下一个位置
        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        boolean firstCard = revealFirst;
        for (WordCardData cardData : cardsData) {
            VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(cardData.getWord());

            // 构建卡片实体
            VocabularyCard card = VocabularyCard.builder()
                    .conversation(conversation)
                    .vocabularyWordId(vocabularyWord.getId())
                    .word(cardData.getWord())
                    .phonetic(cardData.getPhonetic())
                    .partOfSpeech(cardData.getPartOfSpeech())
                    .meaning(cardData.getMeaning())
                    .example(cardData.getExample())
                    .exampleTranslation(cardData.getExampleTranslation())
                    .chineseSentenceForTranslation(cardData.getExampleTranslation())
                    .category(cardData.getCategory())
                    .difficulty(cardData.getDifficulty())
                    .position(nextPosition)
                    .isCompleted(false)
                    .isRegenerated(false)
                    .isRevealed(firstCard)
                    .regenerationIndex(0)
                    .build();

            if (cardData.getSynonyms() != null && !cardData.getSynonyms().isEmpty()) {
                card.setSynonyms(cardData.getSynonyms());
            }

            VocabularyCard saved = vocabularyCardRepository.save(card);

            // 更新用户词汇记录和记忆
            Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;
            if (userId != null) {
                userVocabularyService.upsertRecordOnly(userId, saved);
                vocabularyMemoryService.recordInteraction(userId, saved, VocabularyMemoryEventType.SEEN);
            }

            savedCards.add(toDTO(saved));
            nextPosition++;
            firstCard = false;
        }

        return savedCards;
    }

    // 将实体转换为DTO
    private VocabularyCardDTO toDTO(VocabularyCard card) {
        return VocabularyCardDTO.builder()
                .id(card.getId())
                .conversationId(card.getConversation().getId())
                .word(card.getWord())
                .phonetic(card.getPhonetic())
                .partOfSpeech(card.getPartOfSpeech())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleTranslation(card.getExampleTranslation())
                .synonyms(card.getSynonyms())
                .category(card.getCategory())
                .difficulty(card.getDifficulty())
                .position(card.getPosition())
                .userMeaningGuess(card.getUserMeaningGuess())
                .meaningCheckResult(card.getMeaningCheckResult())
                .meaningIsCorrect(card.getMeaningIsCorrect())
                .meaningCheckCompleted(card.getMeaningCheckCompleted())
                .chineseSentenceForTranslation(card.getChineseSentenceForTranslation())
                .userEnglishSentence(card.getUserEnglishSentence())
                .sentenceAnalysis(card.getSentenceAnalysis())
                .sentenceAnalysisCompleted(card.getSentenceAnalysisCompleted())
                .sentenceHasNewWord(card.getSentenceHasNewWord())
                .sentenceMeaningMatches(card.getSentenceMeaningMatches())
                .isCompleted(card.getIsCompleted())
                .isRevealed(card.getIsRevealed())
                .isRegenerated(card.getIsRegenerated())
                .regenerationIndex(card.getRegenerationIndex())
                .regeneratedWords(List.of())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }
}
