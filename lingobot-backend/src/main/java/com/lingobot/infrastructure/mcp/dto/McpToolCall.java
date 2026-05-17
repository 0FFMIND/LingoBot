package com.lingobot.infrastructure.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具调用请求 DTO
 * 包含调用的工具名称、参数和对话上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolCall {

    private String id;
    private String name;
    private Map<String, Object> arguments;
    private String conversationId;
}
