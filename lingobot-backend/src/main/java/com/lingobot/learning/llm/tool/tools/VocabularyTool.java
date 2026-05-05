package com.lingobot.learning.llm.tool.tools;

import com.lingobot.learning.llm.tool.dto.McpTool;
import com.lingobot.learning.llm.tool.dto.McpToolCall;
import com.lingobot.learning.llm.tool.dto.McpToolResult;
import com.lingobot.learning.llm.tool.service.McpToolHandler;
import com.lingobot.learning.llm.tool.service.ToolCategory;
import com.lingobot.learning.llm.tool.service.ToolMode;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.VocabularyStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 词汇学习 MCP 工具
 * 用于 AI 与前端交互，展示单词卡片和造句反馈
 * 支持两种操作：display_flashcard（展示单词卡）和 display_sentence_feedback（展示造句反馈�? */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyTool implements McpToolHandler {

    private static final String TOOL_NAME = "vocabulary";
    private static final List<String> SUPPORTED_MODES = List.of(
            ToolMode.CHAT,
            ToolMode.AGENT,
            ToolMode.VOCABULARY
    );
    
    /** Redis 缓存键前缀 - 单个单词�?*/
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    /** Redis 缓存键前缀 - 对话的所有有效卡片列�?*/
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    /** Redis 缓存键前缀 - 对话的有效卡片数�?*/
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyStateService vocabularyStateService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.ONE_TIME;
    }

    @Override
    public List<String> getSupportedModes() {
        return SUPPORTED_MODES;
    }

    /**
     * 获取工具定义，描述工具的参数和功�?     * 这些信息会被传递给 AI 模型，让 AI 了解如何调用此工�?     */
    @Override
    public McpTool getToolDefinition() {
        Map<String, McpTool.Property> properties = new HashMap<>();

        properties.put("action", McpTool.Property.builder()
                .type("string")
                .description("操作类型：display_flashcard（展示单词卡，由AI生成单词信息后调用）、display_sentence_feedback（展示造句反馈）、check_meaning_accuracy（检查用户释义准确性）")
                .enums(Arrays.asList(
                        "display_flashcard",
                        "display_sentence_feedback",
                        "check_meaning_accuracy"
                ))
                .build());

        properties.put("word", McpTool.Property.builder()
                .type("string")
                .description("英文单词")
                .build());

        properties.put("phonetic", McpTool.Property.builder()
                .type("string")
                .description("音标（IPA 格式）")
                .build());

        properties.put("partOfSpeech", McpTool.Property.builder()
                .type("string")
                .description("词性，如 n., v., adj., adv., int., conj., prep., pron. 等")
                .build());

        properties.put("meaning", McpTool.Property.builder()
                .type("string")
                .description("中文释义")
                .build());

        properties.put("synonyms", McpTool.Property.builder()
                .type("array")
                .description("同义词列表（数组）")
                .items(McpTool.Items.builder().type("string").build())
                .build());

        properties.put("vocabularyCategory", McpTool.Property.builder()
                .type("string")
                .description("词汇划分标准：cefr（CEFR 等级）、ielts（雅思）、toefl（托福）")
                .enums(Arrays.asList("cefr", "ielts", "toefl"))
                .build());

        properties.put("vocabularyDifficulty", McpTool.Property.builder()
                .type("string")
                .description("难度级别：CEFR 如 a1/a2/b1/b2/c1/c2；IELTS 如；TOEFL 如 beginner/intermediate/advanced/expert")
                .build());

        properties.put("sentence", McpTool.Property.builder()
                .type("string")
                .description("用户造的句子（用于display_sentence_feedback）")
                .build());

        properties.put("current_word", McpTool.Property.builder()
                .type("string")
                .description("当前学习的单词（用于 display_sentence_feedback）")
                .build());

        properties.put("feedback", McpTool.Property.builder()
                .type("string")
                .description("AI 对用户造句的反馈（用于 display_sentence_feedback）")
                .build());

        properties.put("example", McpTool.Property.builder()
                .type("string")
                .description("参考例句（用于 display_sentence_feedback）")
                .build());

        properties.put("exampleTranslation", McpTool.Property.builder()
                .type("string")
                .description("例句中文翻译（用于display_sentence_feedback）")
                .build());

        properties.put("user_meaning", McpTool.Property.builder()
                .type("string")
                .description("用户输入的中文释义（用于 check_meaning_accuracy）")
                .build());

        properties.put("is_correct", McpTool.Property.builder()
                .type("boolean")
                .description("用户释义是否正确（用于check_meaning_accuracy）")
                .build());

        properties.put("check_feedback", McpTool.Property.builder()
                .type("string")
                .description("对用户释义的详细反馈（用于check_meaning_accuracy）")
                .build());

        // 构建工具定义，返回给前端和 AI 模型
        return McpTool.builder()
                .name(TOOL_NAME)
                .description("英语词汇学习工具。用于展示单词卡片和造句反馈。AI 应该：1. 生成单词、音标、词性、释义、同义词、词汇划分标准和难度级别后调用 display_flashcard；2. 用户造句后，分析句子并调用 display_sentence_feedback 展示反馈")
                .arguments(McpTool.ToolArguments.builder()
                        .type("object")
                        .properties(properties)
                        .required(Collections.singletonList("action"))
                        .build())
                .build();
    }

    @Override
    public McpToolResult execute(McpToolCall call) {
        Map<String, Object> args = call.getArguments();
        String action = args != null && args.containsKey("action")
                ? (String) args.get("action")
                : "display_flashcard";

        Long conversationId = parseConversationId(call.getConversationId());
        log.info("VocabularyTool action: {}, conversationId: {}", action, conversationId);

        try {
            Map<String, Object> result;
            switch (action) {
                case "display_flashcard":
                    result = displayFlashcard(args, conversationId);
                    break;
                case "display_sentence_feedback":
                    result = displaySentenceFeedback(args, conversationId);
                    break;
                case "check_meaning_accuracy":
                    result = checkMeaningAccuracy(args, conversationId);
                    break;
                default:
                    result = displayFlashcard(args, conversationId);
            }

            String resultJson = objectMapper.writeValueAsString(result);

            return McpToolResult.builder()
                    .id(call.getId())
                    .name(TOOL_NAME)
                    .success(true)
                    .content(Collections.singletonList(
                            McpToolResult.Content.builder()
                                    .type("text")
                                    .text(resultJson)
                                    .build()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Error executing vocabulary tool", e);
            return McpToolResult.builder()
                    .id(call.getId())
                    .name(TOOL_NAME)
                    .success(false)
                    .error("Failed to execute vocabulary tool: " + e.getMessage())
                    .build();
        }
    }

    private Long parseConversationId(String conversationIdStr) {
        if (conversationIdStr == null || conversationIdStr.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(conversationIdStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid conversationId: {}", conversationIdStr);
            return null;
        }
    }

    private Map<String, Object> displayFlashcard(Map<String, Object> args, Long conversationId) {
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
        String vocabularyCategory = args != null && args.containsKey("vocabularyCategory")
                ? (String) args.get("vocabularyCategory")
                : "cefr";
        String vocabularyDifficulty = args != null && args.containsKey("vocabularyDifficulty")
                ? (String) args.get("vocabularyDifficulty")
                : "b2";
        // 解析同义词参数
        List<String> synonyms = new ArrayList<>();
        if (args != null && args.containsKey("synonyms")) {
            Object synonymsObj = args.get("synonyms");
            if (synonymsObj instanceof List) {
                synonyms = (List<String>) synonymsObj;
            }
        }

        // 保存当前单词状态到会话中，用于后续造句反馈时的上下文参数
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
        result.put("synonyms", synonyms);
        result.put("vocabularyCategory", vocabularyCategory);
        result.put("vocabularyDifficulty", vocabularyDifficulty);
        result.put("display_mode", "word_only");
        result.put("message", word != null && phonetic != null 
                ? String.format("%s [%s]", word, phonetic) 
                : word);

        return result;
    }

    private Map<String, Object> displaySentenceFeedback(Map<String, Object> args, Long conversationId) {
        String userSentence = args != null && args.containsKey("sentence")
                ? (String) args.get("sentence")
                : "";
        String currentWord = args != null && args.containsKey("current_word")
                ? (String) args.get("current_word")
                : "";
        String feedback = args != null && args.containsKey("feedback")
                ? (String) args.get("feedback")
                : "";
        String example = args != null && args.containsKey("example")
                ? (String) args.get("example")
                : "";
        String exampleTranslation = args != null && args.containsKey("exampleTranslation")
                ? (String) args.get("exampleTranslation")
                : "";
        
        Map<String, Object> cachedState = null;
        if (conversationId != null) {
            cachedState = vocabularyStateService.getCurrentWord(conversationId);
            log.info("Retrieved cached state for conversation {}: {}", conversationId, 
                    cachedState != null ? cachedState.get("word") : "null");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("action", "display_sentence_feedback");
        result.put("sentence", userSentence);
        result.put("current_word", currentWord);
        result.put("feedback", feedback);
        result.put("example", example);
        result.put("exampleTranslation", exampleTranslation);
        result.put("display_mode", "sentence_feedback");
        
        if (args != null && args.containsKey("word")) {
            result.put("word", args.get("word"));
        } else if (cachedState != null && cachedState.containsKey("word")) {
            result.put("word", cachedState.get("word"));
        } else {
            result.put("word", currentWord);
        }
        
        if (args != null && args.containsKey("phonetic")) {
            result.put("phonetic", args.get("phonetic"));
        } else if (cachedState != null && cachedState.containsKey("phonetic")) {
            result.put("phonetic", cachedState.get("phonetic"));
        }
        
        if (args != null && args.containsKey("partOfSpeech")) {
            result.put("partOfSpeech", args.get("partOfSpeech"));
        } else if (cachedState != null && cachedState.containsKey("partOfSpeech")) {
            result.put("partOfSpeech", cachedState.get("partOfSpeech"));
        }
        
        if (args != null && args.containsKey("meaning")) {
            result.put("meaning", args.get("meaning"));
        } else if (cachedState != null && cachedState.containsKey("meaning")) {
            result.put("meaning", cachedState.get("meaning"));
        }
        
        if (args != null && args.containsKey("vocabularyCategory")) {
            result.put("vocabularyCategory", args.get("vocabularyCategory"));
        } else if (cachedState != null && cachedState.containsKey("vocabularyCategory")) {
            result.put("vocabularyCategory", cachedState.get("vocabularyCategory"));
        }
        
        if (args != null && args.containsKey("vocabularyDifficulty")) {
            result.put("vocabularyDifficulty", args.get("vocabularyDifficulty"));
        } else if (cachedState != null && cachedState.containsKey("vocabularyDifficulty")) {
            result.put("vocabularyDifficulty", cachedState.get("vocabularyDifficulty"));
        }
        
        List<String> synonyms = new ArrayList<>();
        if (args != null && args.containsKey("synonyms")) {
            Object synonymsObj = args.get("synonyms");
            if (synonymsObj instanceof List) {
                synonyms = (List<String>) synonymsObj;
            }
        } else if (cachedState != null && cachedState.containsKey("synonyms")) {
            Object synonymsObj = cachedState.get("synonyms");
            if (synonymsObj instanceof List) {
                synonyms = (List<String>) synonymsObj;
            }
        }
        result.put("synonyms", synonyms);

        // 保存用户造句�?AI 反馈到数据库
        if (conversationId != null && !feedback.isEmpty()) {
            try {
                List<VocabularyCard> incompleteCards = vocabularyCardRepository
                        .findIncompleteByConversationId(conversationId);
                Optional<VocabularyCard> targetCard = incompleteCards.stream()
                        .filter(c -> currentWord.equalsIgnoreCase(c.getWord()))
                        .findFirst();
                if (!targetCard.isPresent() && !incompleteCards.isEmpty()) {
                    targetCard = Optional.of(incompleteCards.get(0));
                }
                if (targetCard.isPresent()) {
                    VocabularyCard card = targetCard.get();
                    card.setAiFeedback(feedback);
                    if (!example.isEmpty()) card.setExample(example);
                    if (!exampleTranslation.isEmpty()) card.setExampleTranslation(exampleTranslation);
                    vocabularyCardRepository.save(card);
                    evictCardAndConversationCache(card.getId(), conversationId);
                    log.info("Saved AI feedback to vocabulary card id={} word={}", card.getId(), card.getWord());
                } else {
                    log.warn("No vocabulary card found to save feedback for conversation={}", conversationId);
                }
            } catch (Exception e) {
                log.error("Failed to save AI feedback to vocabulary card", e);
            }
        }

        return result;
    }

    private Map<String, Object> checkMeaningAccuracy(Map<String, Object> args, Long conversationId) {
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

        if (args != null && args.containsKey("word")) {
            result.put("word", args.get("word"));
        } else if (cachedState != null && cachedState.containsKey("word")) {
            result.put("word", cachedState.get("word"));
        }

        if (args != null && args.containsKey("meaning")) {
            result.put("correct_meaning", args.get("meaning"));
        } else if (cachedState != null && cachedState.containsKey("meaning")) {
            result.put("correct_meaning", cachedState.get("meaning"));
        }

        String wordToMatch = targetWord != null && !targetWord.isEmpty() ? targetWord
                : (cachedState != null && cachedState.containsKey("word") ? (String) cachedState.get("word") : "");

        if (conversationId != null && isCorrect != null) {
            try {
                List<VocabularyCard> incompleteCards = vocabularyCardRepository
                        .findIncompleteByConversationId(conversationId);
                Optional<VocabularyCard> targetCard = Optional.empty();

                if (!wordToMatch.isEmpty()) {
                    targetCard = incompleteCards.stream()
                            .filter(c -> wordToMatch.equalsIgnoreCase(c.getWord()))
                            .findFirst();
                }
                if (!targetCard.isPresent() && !incompleteCards.isEmpty()) {
                    targetCard = Optional.of(incompleteCards.get(0));
                }

                if (targetCard.isPresent()) {
                    VocabularyCard card = targetCard.get();
                    card.setMeaningIsCorrect(isCorrect);
                    if (!checkFeedback.isEmpty()) {
                        card.setMeaningCheckResult(checkFeedback);
                    }
                    card.setMeaningCheckCompleted(true);
                    vocabularyCardRepository.save(card);
                    evictCardAndConversationCache(card.getId(), conversationId);
                    log.info("Saved meaning check result to vocabulary card id={} word={}, isCorrect={}",
                            card.getId(), card.getWord(), isCorrect);
                } else {
                    log.warn("No vocabulary card found to save meaning check for conversation={}", conversationId);
                }
            } catch (Exception e) {
                log.error("Failed to save meaning check to vocabulary card", e);
            }
        }

        return result;
    }
    
    /**
     * 清除单词卡及其所属对话的所有缓�?     * 用于更新/删除操作
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
