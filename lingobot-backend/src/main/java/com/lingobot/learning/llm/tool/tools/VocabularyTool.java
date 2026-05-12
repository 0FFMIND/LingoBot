package com.lingobot.learning.llm.tool.tools;

import com.lingobot.learning.llm.tool.dto.McpTool;
import com.lingobot.learning.llm.tool.dto.McpToolCall;
import com.lingobot.learning.llm.tool.dto.McpToolResult;
import com.lingobot.learning.llm.tool.service.McpToolHandler;
import com.lingobot.learning.llm.tool.service.ToolCategory;
import com.lingobot.learning.llm.tool.service.ToolMode;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.service.VocabularyStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 词汇学习 MCP 工具
 * 用于 AI 与前端交互，展示单词卡片
 * 支持操作：display_flashcard（展示单词卡）、check_meaning_accuracy（检查用户释义准确性） */
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
    
    /** Redis 缓存键前缀 - 单个单词卡*/
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    /** Redis 缓存键前缀 - 对话的所有有效卡片列表*/
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    /** Redis 缓存键前缀 - 对话的有效卡片数量*/
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyStateService vocabularyStateService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyService userVocabularyService;
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
     * 获取工具定义，描述工具的参数和功能
     * 这些信息会被传递给 AI 模型，让 AI 了解如何调用此工具
     */
    @Override
    public McpTool getToolDefinition() {
        Map<String, McpTool.Property> properties = new HashMap<>();

        properties.put("action", McpTool.Property.builder()
                .type("string")
                .description("操作类型：display_flashcard（展示单词卡）、check_meaning_accuracy（检查用户释义准确性）、analyze_sentence（分析用户英文造句）")
                .enums(Arrays.asList(
                        "display_flashcard",
                        "check_meaning_accuracy",
                        "analyze_sentence"
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

        properties.put("example", McpTool.Property.builder()
                .type("string")
                .description("使用该单词的自然英文例句，难度须与单词难度匹配")
                .build());

        properties.put("exampleTranslation", McpTool.Property.builder()
                .type("string")
                .description("英文例句的准确中文翻译")
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
                .description("难度级别：CEFR 使用 a1/a2/b1/b2/c1/c2；IELTS 使用 4.0-5.0/5.5-6.5/7.0-8.0/8.5-9.0；TOEFL 使用 60-80/81-100/101-110/111-120")
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

        properties.put("chineseSentenceForTranslation", McpTool.Property.builder()
                .type("string")
                .description("一个自然的中文句子，供用户翻译成英文（用于check_meaning_accuracy），难度须与单词难度匹配")
                .build());

        properties.put("meaning_matches", McpTool.Property.builder()
                .type("boolean")
                .description("用户英文句子的意思是否与中文例句匹配（用于analyze_sentence）")
                .build());

        properties.put("has_new_word", McpTool.Property.builder()
                .type("boolean")
                .description("用户英文句子是否正确包含新单词（用于analyze_sentence）")
                .build());

        properties.put("feedback", McpTool.Property.builder()
                .type("string")
                .description("对用户英文句子的详细中文反馈，指出优点和改进建议（用于analyze_sentence）")
                .build());

        // 构建工具定义，返回给前端和 AI 模型
        return McpTool.builder()
                .name(TOOL_NAME)
                .description("英语词汇学习工具。用于展示单词卡片。AI 应该：1. 生成单词、音标、词性、释义、同义词、词汇划分标准和难度级别后调用 display_flashcard")
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
                case "check_meaning_accuracy":
                    result = checkMeaningAccuracy(args, conversationId);
                    break;
                case "analyze_sentence":
                    result = analyzeSentence(args, conversationId);
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
        String chineseSentenceForTranslation = args != null && args.containsKey("chineseSentenceForTranslation")
                ? (String) args.get("chineseSentenceForTranslation")
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

        if (!chineseSentenceForTranslation.isEmpty()) {
            result.put("chineseSentenceForTranslation", chineseSentenceForTranslation);
        }

        // 持久化检查结果到数据库
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
                    int updatedRows = chineseSentenceForTranslation.isEmpty()
                            ? vocabularyCardRepository.updateMeaningCheckResult(
                                    card.getId(), isCorrect, checkFeedback)
                            : vocabularyCardRepository.updateMeaningCheckResultWithChineseSentence(
                                    card.getId(), isCorrect, checkFeedback, chineseSentenceForTranslation);
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
                log.error("Failed to persist meaning check result in VocabularyTool", e);
            }
        }

        return result;
    }
    
    private Map<String, Object> analyzeSentence(Map<String, Object> args, Long conversationId) {
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
                List<com.lingobot.learning.vocabulary.entity.VocabularyCard> candidates =
                        vocabularyCardRepository.findIncompleteByConversationId(conversationId);
                final String wordToMatch = word;
                java.util.Optional<com.lingobot.learning.vocabulary.entity.VocabularyCard> target =
                        candidates.stream()
                                .filter(c -> wordToMatch != null && wordToMatch.equalsIgnoreCase(c.getWord()))
                                .findFirst();
                if (!target.isPresent() && !candidates.isEmpty()) {
                    target = java.util.Optional.of(candidates.get(0));
                }
                if (target.isPresent()) {
                    com.lingobot.learning.vocabulary.entity.VocabularyCard card = target.get();
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
                log.error("Failed to persist sentence analysis result in VocabularyTool", e);
            }
        }

        return result;
    }

    /**
     * 清除单词卡及其所属对话的所有缓存
     * 用于更新/删除操作
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
