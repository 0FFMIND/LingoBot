package com.lingobot.learning.vocabulary.graph.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.tool.service.McpService;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryPromptBuilder;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryRecord;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import com.lingobot.learning.vocabulary.graph.VocabularyState;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.service.VocabularyPromptService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyNodeActions {

    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    private static final String DEFAULT_VOCABULARY_DIFFICULTY = "b2";
    private static final String DEFAULT_MODEL = "qwen/qwen3.5-flash-20260224";
    private static final int MAX_RETRIES = 3;

    private final VocabularyMemoryService vocabularyMemoryService;
    private final UserPreferenceService userPreferenceService;
    private final VocabularyPromptService vocabularyPromptService;
    private final VocabularyMemoryPromptBuilder memoryPromptBuilder;
    private final McpService mcpService;
    private final ToolLoopService toolLoopService;
    private final ConversationRepository conversationRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final VocabularyWordService vocabularyWordService;
    private final UserVocabularyService userVocabularyService;
    private final ObjectMapper objectMapper;

    public AsyncNodeAction<VocabularyState> memoryRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing MEMORY_RECALL for conversation: {}, user: {}",
                    state.getConversationId(), state.getUserId());

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

                log.info("Memory recall completed: total memory items={}, excluded words={}",
                        memoryContext.totalMemoryItems(), excludedWords.size());

            } catch (Exception e) {
                log.warn("Memory recall failed, using empty context", e);
                updates.put("memoryContext", null);
                updates.put("excludedWords", new ArrayList<String>());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> planning() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing PLANNING for conversation: {}", state.getConversationId());

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
                    .excludeWords(state.getExcludedWords())
                    .build();
            updates.put("constraints", constraints);

            String systemPrompt = vocabularyPromptService.getDisplayFlashcardPrompt(category, difficulty);

            if (state.getMemoryContext() != null) {
                String memoryPrompt = memoryPromptBuilder.buildPromptContext(state.getMemoryContext());
                if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + memoryPrompt;
                }
            }

            updates.put("systemPrompt", systemPrompt);

            log.info("Planning completed: category={}, difficulty={}, model={}", category, difficulty, model);
            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> generation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing GENERATION for conversation: {}, model: {}, retry: {}",
                    state.getConversationId(), state.getModel(), state.getRetryCount());

            Map<String, Object> updates = new HashMap<>();

            try {
                List<OpenAiChatMessage> messages = new ArrayList<>();
                messages.add(OpenAiChatMessage.createTextMessage("system", state.getSystemPrompt()));

                List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary", "display_flashcard");

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
                    log.info("Generation completed: word={}", card.getWord());
                } else {
                    log.warn("AI did not return valid tool call");
                    updates.put("generationError", "Invalid AI response");
                }

            } catch (Exception e) {
                log.error("Generation failed", e);
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
            log.info("Executing VALIDATION for word: {}", card != null ? card.getWord() : "null");

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

                if (card.getWord() != null && state.getExcludedWords() != null) {
                    String normalizedWord = card.getWord().toLowerCase().trim();
                    if (state.getExcludedWords().contains(normalizedWord)) {
                        errors.add("Word '" + card.getWord() + "' is in excluded list");
                    }
                }
            }

            updates.put("validationErrors", errors);
            updates.put("isValid", errors.isEmpty());

            if (errors.isEmpty()) {
                log.info("Validation passed for word: {}", card.getWord());
            } else {
                log.warn("Validation failed: {}", errors);
                int retryCount = state.getRetryCount() + 1;
                updates.put("retryCount", retryCount);
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> persistence() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            WordCardData card = state.getGeneratedCard();

            log.info("Executing PERSISTENCE for word: {}", card.getWord());

            try {
                VocabularyCardDTO saved = createCardInternal(state.getConversationId(), card, true);
                updates.put("savedCard", saved);

                log.info("Persistence completed: cardId={}, word={}", saved.getId(), saved.getWord());

            } catch (Exception e) {
                log.error("Persistence failed", e);
                updates.put("persistenceError", e.getMessage());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyState> fallback() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.warn("Executing FALLBACK for conversation: {}", state.getConversationId());
            Map<String, Object> updates = new HashMap<>();

            String category = state.getCategory() != null ? state.getCategory() : DEFAULT_VOCABULARY_CATEGORY;
            String difficulty = state.getDifficulty() != null ? state.getDifficulty() : "a1";

            WordCardData defaultCard = WordCardData.builder()
                    .word("hello")
                    .phonetic("həˈloʊ")
                    .partOfSpeech("int.")
                    .meaning("你好")
                    .example("Hello, how are you?")
                    .exampleTranslation("你好，你好吗？")
                    .synonyms(List.of("hi", "hey"))
                    .category(category)
                    .difficulty(difficulty)
                    .build();

            updates.put("generatedCard", defaultCard);

            try {
                VocabularyCardDTO saved = createCardInternal(state.getConversationId(), defaultCard, true);
                updates.put("savedCard", saved);
                log.info("Fallback persistence completed: cardId={}", saved.getId());
            } catch (Exception e) {
                log.error("Fallback persistence failed", e);
            }

            return updates;
        });
    }

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

    public boolean shouldFallback(VocabularyState state) {
        return !state.isValid() && state.getRetryCount() >= MAX_RETRIES;
    }

    @Transactional
    private VocabularyCardDTO createCardInternal(Long conversationId, WordCardData cardData, boolean isRevealed) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(cardData.getWord());

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
                .isRevealed(isRevealed)
                .regenerationIndex(0)
                .build();

        if (cardData.getSynonyms() != null && !cardData.getSynonyms().isEmpty()) {
            card.setSynonyms(cardData.getSynonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(card);

        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;
        if (userId != null) {
            userVocabularyService.upsertRecordOnly(userId, saved);
            vocabularyMemoryService.recordInteraction(userId, saved, VocabularyMemoryEventType.SEEN);
        }

        return toDTO(saved);
    }

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
