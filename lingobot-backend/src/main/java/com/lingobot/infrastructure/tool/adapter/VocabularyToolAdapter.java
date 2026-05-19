package com.lingobot.infrastructure.tool.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolCategory;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolHandler;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.lingobot.learning.vocabulary.service.VocabularyToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.lingobot.infrastructure.tool.dto.ToolMode.AGENT;
import static com.lingobot.infrastructure.tool.dto.ToolMode.CHAT;

/**
 * 词汇学习工具适配器。
 *
 * 作为工具系统与词汇业务的适配层，负责定义 schema、解析参数、转发调用。
 * 实现 ToolHandler 接口，将词汇学习相关的业务能力封装为可被 AI 调用的工具，
 * 支持单词卡展示、释义检查、句子分析等多种操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyToolAdapter implements ToolHandler {

    // 工具名称标识
    private static final String TOOL_NAME = "vocabulary";

    // 支持的对话模式列表：普通聊天、Agent
    private static final List<String> SUPPORTED_MODES = List.of(
            CHAT,
            AGENT
    );

    // JSON 序列化工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 词汇学习业务服务
    private final VocabularyToolService vocabularyToolService;

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

    // 获取工具定义，描述工具的参数和功能，构建完整的 JSON Schema 供 AI 使用
    @Override
    public ToolDefinition getToolDefinition() {
        Map<String, ToolDefinition.Property> properties = new HashMap<>();

        properties.put("action", ToolDefinition.Property.builder()
                .type("string")
                .description("操作类型：display_flashcard（展示单词卡）、display_flashcard_batch（批量展示单词卡）、check_meaning_accuracy（检查用户释义准确性）、analyze_sentence（分析用户英文造句）")
                .enums(Arrays.asList(
                        "display_flashcard",
                        "display_flashcard_batch",
                        "check_meaning_accuracy",
                        "analyze_sentence"
                ))
                .build());

        properties.put("cards", ToolDefinition.Property.builder()
                .type("array")
                .description("单词卡数组（用于 display_flashcard_batch），每个元素包含 word、phonetic、partOfSpeech、meaning、example、exampleTranslation、synonyms、vocabularyCategory、vocabularyDifficulty")
                .items(ToolDefinition.Items.builder().type("object").build())
                .build());

        properties.put("word", ToolDefinition.Property.builder()
                .type("string")
                .description("英文单词")
                .build());

        properties.put("phonetic", ToolDefinition.Property.builder()
                .type("string")
                .description("音标（IPA 格式）")
                .build());

        properties.put("partOfSpeech", ToolDefinition.Property.builder()
                .type("string")
                .description("词性，如 n., v., adj., adv., int., conj., prep., pron. 等")
                .build());

        properties.put("meaning", ToolDefinition.Property.builder()
                .type("string")
                .description("中文释义")
                .build());

        properties.put("example", ToolDefinition.Property.builder()
                .type("string")
                .description("使用该单词的自然英文例句，难度须与单词难度匹配")
                .build());

        properties.put("exampleTranslation", ToolDefinition.Property.builder()
                .type("string")
                .description("英文例句的准确中文翻译")
                .build());

        properties.put("synonyms", ToolDefinition.Property.builder()
                .type("array")
                .description("同义词列表（数组）")
                .items(ToolDefinition.Items.builder().type("string").build())
                .build());

        properties.put("vocabularyCategory", ToolDefinition.Property.builder()
                .type("string")
                .description("词汇划分标准：cefr（CEFR 等级）、ielts（雅思）、toefl（托福）")
                .enums(Arrays.asList("cefr", "ielts", "toefl"))
                .build());

        properties.put("vocabularyDifficulty", ToolDefinition.Property.builder()
                .type("string")
                .description("难度级别：CEFR 使用 a1/a2/b1/b2/c1/c2；IELTS 使用 4.0-5.0/5.5-6.5/7.0-8.0/8.5-9.0；TOEFL 使用 60-80/81-100/101-110/111-120")
                .build());

        properties.put("user_meaning", ToolDefinition.Property.builder()
                .type("string")
                .description("用户输入的中文释义（用于 check_meaning_accuracy）")
                .build());

        properties.put("is_correct", ToolDefinition.Property.builder()
                .type("boolean")
                .description("用户释义是否正确（用于check_meaning_accuracy）")
                .build());

        properties.put("check_feedback", ToolDefinition.Property.builder()
                .type("string")
                .description("对用户释义的详细反馈（用于check_meaning_accuracy）")
                .build());

        properties.put("chineseSentenceForTranslation", ToolDefinition.Property.builder()
                .type("string")
                .description("一个自然的中文句子，供用户翻译成英文，难度须与单词难度匹配")
                .build());

        properties.put("meaning_matches", ToolDefinition.Property.builder()
                .type("boolean")
                .description("用户英文句子的意思是否与中文例句匹配（用于analyze_sentence）")
                .build());

        properties.put("has_new_word", ToolDefinition.Property.builder()
                .type("boolean")
                .description("用户英文句子是否正确包含新单词（用于analyze_sentence）")
                .build());

        properties.put("feedback", ToolDefinition.Property.builder()
                .type("string")
                .description("对用户英文句子的详细中文反馈，指出优点和改进建议（用于analyze_sentence）")
                .build());

        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description("英语词汇学习工具。用于展示单词卡片。AI 应该：1. 生成单词、音标、词性、释义、同义词、词汇划分标准和难度级别后调用 display_flashcard")
                .arguments(ToolDefinition.ToolArguments.builder()
                        .type("object")
                        .properties(properties)
                        .required(Collections.singletonList("action"))
                        .build())
                .build();
    }

    // 执行工具调用，根据 action 参数转发到对应业务方法处理
    @Override
    public ToolResult execute(ToolCall call) {
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
                    result = vocabularyToolService.displayFlashcard(args, conversationId);
                    break;
                case "display_flashcard_batch":
                    result = vocabularyToolService.displayFlashcardBatch(args, conversationId);
                    break;
                case "check_meaning_accuracy":
                    result = vocabularyToolService.checkMeaningAccuracy(args, conversationId);
                    break;
                case "analyze_sentence":
                    result = vocabularyToolService.analyzeSentence(args, conversationId);
                    break;
                default:
                    result = vocabularyToolService.displayFlashcard(args, conversationId);
            }

            String resultJson = objectMapper.writeValueAsString(result);

            return ToolResult.builder()
                    .id(call.getId())
                    .name(TOOL_NAME)
                    .success(true)
                    .content(Collections.singletonList(
                            ToolResult.Content.builder()
                                    .type("text")
                                    .text(resultJson)
                                    .build()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Error executing vocabulary tool", e);
            return ToolResult.builder()
                    .id(call.getId())
                    .name(TOOL_NAME)
                    .success(false)
                    .error("Failed to execute vocabulary tool: " + e.getMessage())
                    .build();
        }
    }

    // 解析对话 ID，将字符串转换为 Long，解析失败返回 null
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

    // 窄化词汇工具的参数定义，根据操作类型仅保留必要的参数，约束 action 为固定值
    @Override
    public OpenAiTool narrowOpenAiTool(
            OpenAiTool tool,
            String action) {
        OpenAiTool.Function function = tool.getFunction();
        OpenAiTool.Parameters parameters = function.getParameters();
        if (parameters == null || parameters.getProperties() == null) {
            return tool;
        }

        List<String> allowedKeys = switch (action) {
            case "check_meaning_accuracy" -> List.of(
                    "action", "word", "user_meaning", "is_correct", "check_feedback");
            case "analyze_sentence" -> List.of(
                    "action", "word", "meaning_matches", "has_new_word", "feedback");
            case "display_flashcard_batch" -> List.of(
                    "action", "cards");
            default -> List.of(
                    "action", "word", "phonetic", "partOfSpeech", "meaning", "example", "exampleTranslation",
                    "synonyms", "vocabularyCategory", "vocabularyDifficulty");
        };

        Map<String, OpenAiTool.Property> narrowedProperties = new HashMap<>();
        for (String key : allowedKeys) {
            OpenAiTool.Property property = parameters.getProperties().get(key);
            if (property != null) {
                narrowedProperties.put(key, property);
            }
        }

        OpenAiTool.Property actionProperty = narrowedProperties.get("action");
        if (actionProperty != null) {
            narrowedProperties.put("action", OpenAiTool.Property.builder()
                    .type(actionProperty.getType())
                    .description("固定为 " + action)
                    .enums(List.of(action))
                    .build());
        }

        String description = switch (action) {
            case "check_meaning_accuracy" -> "检查用户中文释义是否准确。";
            case "analyze_sentence" -> "分析用户英文句子是否匹配中文例句，并是否正确使用当前新单词。";
            case "display_flashcard_batch" -> "批量生成并展示多张新的英文单词卡。";
            default -> "生成并展示一张新的英文单词卡。";
        };

        OpenAiTool.Parameters narrowedParameters =
                OpenAiTool.Parameters.builder()
                        .type(parameters.getType())
                        .properties(narrowedProperties)
                        .required(List.of("action"))
                        .build();

        return OpenAiTool.builder()
                .type(tool.getType())
                .function(OpenAiTool.Function.builder()
                        .name(function.getName())
                        .description(description)
                        .parameters(narrowedParameters)
                        .build())
                .build();
    }
}
