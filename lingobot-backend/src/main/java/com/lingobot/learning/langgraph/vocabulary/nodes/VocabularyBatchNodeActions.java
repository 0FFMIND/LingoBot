package com.lingobot.learning.langgraph.vocabulary.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.learning.langgraph.vocabulary.VocabularyBatchState;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.tool.service.McpService;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryPromptBuilder;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryRecord;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardBatchData;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyWord;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyBatchNodeActions {

    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    private static final String DEFAULT_VOCABULARY_DIFFICULTY = "b2";
    private static final String DEFAULT_MODEL = "qwen";
    private static final int MAX_RETRIES = 3;
    private static final int DEFAULT_BATCH_SIZE = 10;

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

    public AsyncNodeAction<VocabularyBatchState> memoryRecall() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing BATCH_MEMORY_RECALL for conversation: {}, user: {}",
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

                log.info("Batch memory recall completed: total memory items={}, excluded words={}",
                        memoryContext.totalMemoryItems(), excludedWords.size());

            } catch (Exception e) {
                log.warn("Batch memory recall failed, using empty context", e);
                updates.put("memoryContext", null);
                updates.put("excludedWords", new ArrayList<String>());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyBatchState> planning() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing BATCH_PLANNING for conversation: {}", state.getConversationId());

            Map<String, Object> updates = new HashMap<>();

            String category = state.getCategory();
            String difficulty = state.getDifficulty();
            String model = state.getModel();
            Integer batchSize = state.getBatchSize();

            if (batchSize == null || batchSize <= 0) {
                batchSize = DEFAULT_BATCH_SIZE;
            }

            if (state.getUserId() != null) {
                UserPreferenceDTO preference = userPreferenceService.getOrCreatePreference(state.getUserId());

                if (category == null || category.isBlank()) {
                    category = preference.getVocabularyCategory();
                }
                if (difficulty == null || difficulty.isBlank()) {
                    difficulty = preference.getVocabularyDifficulty();
                }
                if (model == null || model.isBlank()) {
                    model = preference.getVocabularyModel();
                }
            }

            category = (category == null || category.isBlank()) ? DEFAULT_VOCABULARY_CATEGORY : category.toLowerCase();
            difficulty = (difficulty == null || difficulty.isBlank()) ? DEFAULT_VOCABULARY_DIFFICULTY : difficulty.toLowerCase();
            model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.toLowerCase();

            updates.put("category", category);
            updates.put("difficulty", difficulty);
            updates.put("model", model);
            updates.put("batchSize", batchSize);

            var constraints = VocabularyGenerationConstraints.builder()
                    .category(category)
                    .difficulty(difficulty)
                    .excludeWords(state.getExcludedWords())
                    .build();
            updates.put("constraints", constraints);

            String systemPrompt = vocabularyPromptService.getBatchFlashcardPrompt(category, difficulty, batchSize);

            if (state.getMemoryContext() != null) {
                String memoryPrompt = memoryPromptBuilder.buildPromptContext(state.getMemoryContext());
                if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + memoryPrompt;
                }
            }

            updates.put("systemPrompt", systemPrompt);

            log.info("Batch planning completed: category={}, difficulty={}, model={}, batchSize={}",
                    category, difficulty, model, batchSize);
            return updates;
        });
    }

    public AsyncNodeAction<VocabularyBatchState> generation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.info("Executing BATCH_GENERATION for conversation: {}, model: {}, retry: {}, batchSize: {}",
                    state.getConversationId(), state.getModel(), state.getRetryCount(), state.getBatchSize());

            Map<String, Object> updates = new HashMap<>();

            try {
                List<OpenAiChatMessage> messages = new ArrayList<>();
                messages.add(OpenAiChatMessage.createTextMessage("system", state.getSystemPrompt()));

                List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary", "display_flashcard_batch");

                if (tools == null || tools.isEmpty()) {
                    updates.put("generationError", "No batch vocabulary tool available");
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
                    WordCardBatchData batch = parseWordCardBatchData(result.getToolResultText(), state);
                    updates.put("generatedBatch", batch);
                    log.info("Batch generation completed: card count={}",
                            batch.getCards() != null ? batch.getCards().size() : 0);
                } else {
                    log.warn("AI did not return valid tool call for batch generation");
                    updates.put("generationError", "Invalid AI response");
                }

            } catch (Exception e) {
                log.error("Batch generation failed", e);
                updates.put("generationError", e.getMessage());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyBatchState> validation() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            List<String> errors = new ArrayList<>();

            WordCardBatchData batch = state.getGeneratedBatch();
            log.info("Executing BATCH_VALIDATION for card count: {}",
                    batch != null && batch.getCards() != null ? batch.getCards().size() : 0);

            if (batch == null || batch.getCards() == null || batch.getCards().isEmpty()) {
                errors.add("Generated batch is null or empty");
            } else {
                List<String> wordsInBatch = new ArrayList<>();
                for (int i = 0; i < batch.getCards().size(); i++) {
                    WordCardData card = batch.getCards().get(i);
                    if (card == null) {
                        errors.add("Card at index " + i + " is null");
                        continue;
                    }
                    if (card.getWord() == null || card.getWord().isBlank()) {
                        errors.add("Card at index " + i + ": Word is empty");
                    } else {
                        String normalizedWord = card.getWord().toLowerCase().trim();
                        if (wordsInBatch.contains(normalizedWord)) {
                            errors.add("Card at index " + i + ": Duplicate word '" + card.getWord() + "' in batch");
                        }
                        wordsInBatch.add(normalizedWord);
                    }
                    if (card.getMeaning() == null || card.getMeaning().isBlank()) {
                        errors.add("Card at index " + i + ": Meaning is empty");
                    }
                    if (card.getPartOfSpeech() == null || card.getPartOfSpeech().isBlank()) {
                        errors.add("Card at index " + i + ": PartOfSpeech is empty");
                    }
                    if (card.getExample() == null || card.getExample().isBlank()) {
                        errors.add("Card at index " + i + ": Example is empty");
                    }
                    if (card.getExampleTranslation() == null || card.getExampleTranslation().isBlank()) {
                        errors.add("Card at index " + i + ": ExampleTranslation is empty");
                    }

                    if (card.getWord() != null && state.getExcludedWords() != null) {
                        String normalizedWord = card.getWord().toLowerCase().trim();
                        if (state.getExcludedWords().contains(normalizedWord)) {
                            errors.add("Card at index " + i + ": Word '" + card.getWord() + "' is in excluded list");
                        }
                    }
                }

                if (batch.getCards().size() != state.getBatchSize()) {
                    log.warn("Batch size mismatch: expected={}, actual={}", state.getBatchSize(), batch.getCards().size());
                }
            }

            updates.put("validationErrors", errors);
            updates.put("isValid", errors.isEmpty());

            if (errors.isEmpty()) {
                log.info("Batch validation passed");
            } else {
                log.warn("Batch validation failed: {}", errors);
                int retryCount = state.getRetryCount() + 1;
                updates.put("retryCount", retryCount);
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyBatchState> persistence() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> updates = new HashMap<>();
            WordCardBatchData batch = state.getGeneratedBatch();

            if (batch == null || batch.getCards() == null) {
                updates.put("persistenceError", "Generated batch is null");
                return updates;
            }

            log.info("Executing BATCH_PERSISTENCE for card count: {}", batch.getCards().size());

            try {
                List<VocabularyCardDTO> savedCards = createCardsInternal(state.getConversationId(), batch.getCards(), true);
                updates.put("savedCards", savedCards);
                log.info("Batch persistence completed: saved {} cards", savedCards.size());

            } catch (Exception e) {
                log.error("Batch persistence failed", e);
                updates.put("persistenceError", e.getMessage());
            }

            return updates;
        });
    }

    public AsyncNodeAction<VocabularyBatchState> fallback() {
        return state -> CompletableFuture.supplyAsync(() -> {
            log.warn("Executing BATCH_FALLBACK for conversation: {}", state.getConversationId());
            Map<String, Object> updates = new HashMap<>();

            String category = state.getCategory() != null ? state.getCategory() : DEFAULT_VOCABULARY_CATEGORY;
            String difficulty = state.getDifficulty() != null ? state.getDifficulty() : "a1";

            try {
                String[] fallbackWords = {"hello", "world", "good", "bad", "happy", "sad", "fast", "slow", "big", "small"};
                List<WordCardData> fallbackCards = new ArrayList<>();
                for (int i = 0; i < state.getBatchSize(); i++) {
                    String word = fallbackWords[i % fallbackWords.length];
                    WordCardData cardData = WordCardData.builder()
                            .word(word)
                            .phonetic("fəˈnetɪk")
                            .partOfSpeech("adj.")
                            .meaning(word + "的中文释义")
                            .example("This is a sentence with " + word + ".")
                            .exampleTranslation("这是一个包含" + word + "的句子。")
                            .synonyms(List.of("synonym1", "synonym2"))
                            .category(category)
                            .difficulty(difficulty)
                            .build();
                    fallbackCards.add(cardData);
                }

                List<VocabularyCardDTO> savedCards = createCardsInternal(state.getConversationId(), fallbackCards, true);
                updates.put("savedCards", savedCards);
                log.info("Batch fallback persistence completed: {} cards", savedCards.size());
            } catch (Exception e) {
                log.error("Batch fallback persistence failed", e);
            }

            return updates;
        });
    }

    private List<String> buildExcludedWords(com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext context) {
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

    private Integer merge(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    public boolean shouldRetry(VocabularyBatchState state) {
        return !state.isValid() && state.getRetryCount() < MAX_RETRIES;
    }

    public boolean shouldFallback(VocabularyBatchState state) {
        return !state.isValid() && state.getRetryCount() >= MAX_RETRIES;
    }

    @Transactional
    private List<VocabularyCardDTO> createCardsInternal(Long conversationId, List<WordCardData> cardsData, boolean revealFirst) {
        List<VocabularyCardDTO> savedCards = new ArrayList<>();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        boolean firstCard = revealFirst;
        for (WordCardData cardData : cardsData) {
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
                    .isRevealed(firstCard)
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

            savedCards.add(toDTO(saved));
            nextPosition++;
            firstCard = false;
        }

        return savedCards;
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
