package com.lingobot.infrastructure.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用请求 DTO。
 *
 * 封装 AI 模型发起的工具调用请求，包含调用标识、工具名称、
 * 实际参数和关联的对话上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    // 调用唯一标识，用于匹配调用请求与响应结果
    private String id;

    // 被调用的工具名称，需与 ToolDefinition.name 完全一致
    private String name;

    // 工具调用参数，键为参数名，值为参数值，需符合 ToolDefinition 中定义的参数 schema
    private Map<String, Object> arguments;

    // 关联的对话 ID，用于工具执行时获取对话上下文
    private String conversationId;
}
