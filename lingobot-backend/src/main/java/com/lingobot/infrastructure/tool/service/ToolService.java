package com.lingobot.infrastructure.tool.service;

import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 工具服务
// 提供工具列表查询、工具调用、格式转换等功能
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    // 初始化方法，应用启动时执行
    @PostConstruct
    public void init() {
        log.info("Tool Service initialized");
    }

    // 获取所有工具列表
    public List<ToolDefinition> listTools() {
        return toolRegistry.getAllTools();
    }

    // 获取 OpenAI 格式的工具列表
    public List<OpenAiTool> getOpenAiTools() {
        List<ToolDefinition> toolDefinitions = toolRegistry.getAllTools();
        List<OpenAiTool> openAiTools = new ArrayList<>();

        for (ToolDefinition toolDefinition : toolDefinitions) {
            openAiTools.add(convertToOpenAiTool(toolDefinition));
        }

        log.info("Converted {} tools to OpenAI format", openAiTools.size());
        return openAiTools;
    }

    // 根据模式获取 OpenAI 格式的工具列表
    public List<OpenAiTool> getOpenAiToolsForMode(String mode) {
        List<ToolDefinition> toolDefinitions = toolRegistry.getToolsForMode(mode);
        List<OpenAiTool> openAiTools = new ArrayList<>();

        for (ToolDefinition toolDefinition : toolDefinitions) {
            openAiTools.add(convertToOpenAiTool(toolDefinition));
        }

        log.info("Converted {} tools for mode '{}' to OpenAI format", openAiTools.size(), mode);
        return openAiTools;
    }

    // 根据模式和词汇操作类型获取 OpenAI 格式的工具列表
    public List<OpenAiTool> getOpenAiToolsForMode(String mode, String vocabularyAction) {
        List<OpenAiTool> tools = getOpenAiToolsForMode(mode);
        if (!"vocabulary".equals(mode) || vocabularyAction == null || vocabularyAction.isBlank()) {
            return tools;
        }

        return tools.stream()
                .map(tool -> "vocabulary".equals(tool.getFunction().getName())
                        ? narrowVocabularyTool(tool, vocabularyAction)
                        : tool)
                .toList();
    }

    // 调用指定的工具
    public ToolResult callTool(ToolCall call) {
        log.info("Calling tool: {}, id: {}", call.getName(), call.getId());
        return toolRegistry.executeTool(call);
    }

    // 将工具定义转换为 OpenAI 格式
    private OpenAiTool convertToOpenAiTool(ToolDefinition toolDefinition) {
        OpenAiTool.Function.FunctionBuilder functionBuilder = OpenAiTool.Function.builder()
                .name(toolDefinition.getName())
                .description(toolDefinition.getDescription());

        if (toolDefinition.getArguments() != null) {
            OpenAiTool.Parameters.ParametersBuilder paramsBuilder = OpenAiTool.Parameters.builder()
                    .type(toolDefinition.getArguments().getType());

            if (toolDefinition.getArguments().getProperties() != null) {
                Map<String, OpenAiTool.Property> properties = new HashMap<>();
                for (Map.Entry<String, ToolDefinition.Property> entry : toolDefinition.getArguments().getProperties().entrySet()) {
                    properties.put(entry.getKey(), convertProperty(entry.getValue()));
                }
                paramsBuilder.properties(properties);
            }

            if (toolDefinition.getArguments().getRequired() != null) {
                paramsBuilder.required(new ArrayList<>(toolDefinition.getArguments().getRequired()));
            }

            functionBuilder.parameters(paramsBuilder.build());
        }

        return OpenAiTool.builder()
                .type("function")
                .function(functionBuilder.build())
                .build();
    }

    // 窄化词汇工具的参数定义
    private OpenAiTool narrowVocabularyTool(
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

    // 将工具属性定义转换为 OpenAI 格式
    private OpenAiTool.Property convertProperty(ToolDefinition.Property property) {
        OpenAiTool.Property.PropertyBuilder builder = OpenAiTool.Property.builder()
                .type(property.getType())
                .description(property.getDescription());

        if (property.getEnums() != null) {
            builder.enums(new ArrayList<>(property.getEnums()));
        }

        if (property.getItems() != null) {
            OpenAiTool.Items items = OpenAiTool.Items.builder()
                    .type(property.getItems().getType())
                    .enums(property.getItems().getEnums() != null ? new ArrayList<>(property.getItems().getEnums()) : null)
                    .build();
            builder.items(items);
        }

        return builder.build();
    }
}
