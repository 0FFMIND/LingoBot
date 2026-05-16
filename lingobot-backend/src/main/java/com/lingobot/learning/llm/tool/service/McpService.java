package com.lingobot.learning.llm.tool.service;

import com.lingobot.learning.llm.tool.dto.McpTool;
import com.lingobot.learning.llm.tool.dto.McpToolCall;
import com.lingobot.learning.llm.tool.dto.McpToolResult;
import com.lingobot.learning.llm.tool.dto.McpTool.Property;
import com.lingobot.learning.llm.tool.dto.McpTool.ToolArguments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具服务
 * 提供工具列表查询、工具调用、格式转换等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("MCP Service initialized");
    }

    /**
     * 获取所有MCP 工具列表
     */
    public List<McpTool> listTools() {
        return toolRegistry.getAllTools();
    }

    /**
     * 获取 OpenAI 格式的工具列表     * 用于与OpenAI API 兼容的工具调用     */
    public List<com.lingobot.learning.llm.dto.openai.OpenAiTool> getOpenAiTools() {
        List<McpTool> mcpTools = toolRegistry.getAllTools();
        List<com.lingobot.learning.llm.dto.openai.OpenAiTool> openAiTools = new ArrayList<>();
        
        for (McpTool mcpTool : mcpTools) {
            openAiTools.add(convertToOpenAiTool(mcpTool));
        }
        
        log.info("Converted {} MCP tools to OpenAI format", openAiTools.size());
        return openAiTools;
    }

    /**
     * 根据模式获取 OpenAI 格式的工具列表     */
    public List<com.lingobot.learning.llm.dto.openai.OpenAiTool> getOpenAiToolsForMode(String mode) {
        List<McpTool> mcpTools = toolRegistry.getToolsForMode(mode);
        List<com.lingobot.learning.llm.dto.openai.OpenAiTool> openAiTools = new ArrayList<>();
        
        for (McpTool mcpTool : mcpTools) {
            openAiTools.add(convertToOpenAiTool(mcpTool));
        }
        
        log.info("Converted {} MCP tools for mode '{}' to OpenAI format", openAiTools.size(), mode);
        return openAiTools;
    }

    public List<com.lingobot.learning.llm.dto.openai.OpenAiTool> getOpenAiToolsForMode(String mode, String vocabularyAction) {
        List<com.lingobot.learning.llm.dto.openai.OpenAiTool> tools = getOpenAiToolsForMode(mode);
        if (!"vocabulary".equals(mode) || vocabularyAction == null || vocabularyAction.isBlank()) {
            return tools;
        }

        return tools.stream()
                .map(tool -> "vocabulary".equals(tool.getFunction().getName())
                        ? narrowVocabularyTool(tool, vocabularyAction)
                        : tool)
                .toList();
    }

    /**
     * 调用指定的MCP 工具
     */
    public McpToolResult callTool(McpToolCall call) {
        log.info("Calling tool: {}, id: {}", call.getName(), call.getId());
        return toolRegistry.executeTool(call);
    }

    /**
     * 将 MCP 工具定义转换为OpenAI 格式
     */
    private com.lingobot.learning.llm.dto.openai.OpenAiTool convertToOpenAiTool(McpTool mcpTool) {
        com.lingobot.learning.llm.dto.openai.OpenAiTool.Function.FunctionBuilder functionBuilder = com.lingobot.learning.llm.dto.openai.OpenAiTool.Function.builder()
                .name(mcpTool.getName())
                .description(mcpTool.getDescription());

        if (mcpTool.getArguments() != null) {
            com.lingobot.learning.llm.dto.openai.OpenAiTool.Parameters.ParametersBuilder paramsBuilder = com.lingobot.learning.llm.dto.openai.OpenAiTool.Parameters.builder()
                    .type(mcpTool.getArguments().getType());

            if (mcpTool.getArguments().getProperties() != null) {
                Map<String, com.lingobot.learning.llm.dto.openai.OpenAiTool.Property> properties = new HashMap<>();
                for (Map.Entry<String, McpTool.Property> entry : mcpTool.getArguments().getProperties().entrySet()) {
                    properties.put(entry.getKey(), convertProperty(entry.getValue()));
                }
                paramsBuilder.properties(properties);
            }

            if (mcpTool.getArguments().getRequired() != null) {
                paramsBuilder.required(new ArrayList<>(mcpTool.getArguments().getRequired()));
            }

            functionBuilder.parameters(paramsBuilder.build());
        }

        return com.lingobot.learning.llm.dto.openai.OpenAiTool.builder()
                .type("function")
                .function(functionBuilder.build())
                .build();
    }

    private com.lingobot.learning.llm.dto.openai.OpenAiTool narrowVocabularyTool(
            com.lingobot.learning.llm.dto.openai.OpenAiTool tool,
            String action) {
        com.lingobot.learning.llm.dto.openai.OpenAiTool.Function function = tool.getFunction();
        com.lingobot.learning.llm.dto.openai.OpenAiTool.Parameters parameters = function.getParameters();
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

        Map<String, com.lingobot.learning.llm.dto.openai.OpenAiTool.Property> narrowedProperties = new HashMap<>();
        for (String key : allowedKeys) {
            com.lingobot.learning.llm.dto.openai.OpenAiTool.Property property = parameters.getProperties().get(key);
            if (property != null) {
                narrowedProperties.put(key, property);
            }
        }

        com.lingobot.learning.llm.dto.openai.OpenAiTool.Property actionProperty = narrowedProperties.get("action");
        if (actionProperty != null) {
            narrowedProperties.put("action", com.lingobot.learning.llm.dto.openai.OpenAiTool.Property.builder()
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

        com.lingobot.learning.llm.dto.openai.OpenAiTool.Parameters narrowedParameters =
                com.lingobot.learning.llm.dto.openai.OpenAiTool.Parameters.builder()
                        .type(parameters.getType())
                        .properties(narrowedProperties)
                        .required(List.of("action"))
                        .build();

        return com.lingobot.learning.llm.dto.openai.OpenAiTool.builder()
                .type(tool.getType())
                .function(com.lingobot.learning.llm.dto.openai.OpenAiTool.Function.builder()
                        .name(function.getName())
                        .description(description)
                        .parameters(narrowedParameters)
                        .build())
                .build();
    }

    /**
     * 将 MCP 属性定义转换为 OpenAI 格式
     */
    private com.lingobot.learning.llm.dto.openai.OpenAiTool.Property convertProperty(McpTool.Property mcpProp) {
        com.lingobot.learning.llm.dto.openai.OpenAiTool.Property.PropertyBuilder builder = com.lingobot.learning.llm.dto.openai.OpenAiTool.Property.builder()
                .type(mcpProp.getType())
                .description(mcpProp.getDescription());

        if (mcpProp.getEnums() != null) {
            builder.enums(new ArrayList<>(mcpProp.getEnums()));
        }

        if (mcpProp.getItems() != null) {
            com.lingobot.learning.llm.dto.openai.OpenAiTool.Items items = com.lingobot.learning.llm.dto.openai.OpenAiTool.Items.builder()
                    .type(mcpProp.getItems().getType())
                    .enums(mcpProp.getItems().getEnums() != null ? new ArrayList<>(mcpProp.getItems().getEnums()) : null)
                    .build();
            builder.items(items);
        }

        return builder.build();
    }
}
