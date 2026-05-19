package com.lingobot.infrastructure.tool.service;

import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolCategory;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolHandler;
import com.lingobot.infrastructure.tool.dto.ToolMode;
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

/**
 * 工具服务。
 *
 * 提供工具列表查询、工具调用、格式转换等功能。
 * 作为工具系统的门面服务，封装 ToolRegistry 的调用，
 * 并提供将内部 ToolDefinition 转换为 OpenAI Function Calling 格式的能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    // 工具注册表
    private final ToolRegistry toolRegistry;

    // JSON 序列化工具
    private final ObjectMapper objectMapper;

    // 初始化方法，应用启动时执行，打印启动日志
    @PostConstruct
    public void init() {
        log.info("Tool Service initialized");
    }

    // 获取所有工具列表，返回通用格式的工具定义
    public List<ToolDefinition> listTools() {
        return toolRegistry.getAllTools();
    }

    // 获取 OpenAI 格式的工具列表，将所有工具转换为 OpenAI Function Calling 格式
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

        log.info("Converted {} tools to OpenAI format for mode: {}", openAiTools.size(), mode);
        return openAiTools;
    }

    // 获取指定名称的工具，根据动作窄化参数定义
    public List<OpenAiTool> getOpenAiTool(String toolName, String action) {
        return getOpenAiTool(toolName, action, ToolMode.CHAT);
    }

    // 获取指定名称的工具，根据动作窄化参数定义，指定模式
    public List<OpenAiTool> getOpenAiTool(String toolName, String action, String mode) {
        ToolHandler handler = toolRegistry.getToolHandler(toolName);
        if (handler == null || !isVisibleForMode(handler, mode)) {
            return List.of();
        }

        OpenAiTool tool = convertToOpenAiTool(handler.getToolDefinition());
        if (action != null && !action.isEmpty()) {
            tool = handler.narrowOpenAiTool(tool, action);
        }

        return List.of(tool);
    }

    private boolean isVisibleForMode(ToolHandler handler, String mode) {
        if (ToolMode.AGENT.equals(mode)) {
            return true;
        }
        return (ToolCategory.ONE_TIME.equals(handler.getCategory())
                || ToolCategory.ALL.equals(handler.getCategory()))
                && handler.supportsMode(mode);
    }

    // 调用指定的工具，转发给 ToolRegistry 执行
    public ToolResult callTool(ToolCall call) {
        log.info("Calling tool: {}, id: {}", call.getName(), call.getId());
        return toolRegistry.executeTool(call);
    }

    // 将工具定义转换为 OpenAI 格式，适配 Function Calling 协议的参数结构
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

    // 将工具属性定义转换为 OpenAI 格式，处理类型、描述、枚举和数组元素
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
