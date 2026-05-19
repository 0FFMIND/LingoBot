package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 聊天请求 DTO。
 *
 * 对应 OpenAI Chat Completions API 的请求体，包含模型选择、消息列表、生成参数等。
 * 所有可选字段使用 @JsonInclude 注解，null 值不会被序列化到 JSON 中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

    // 模型名称（完整名称，如 gpt-4o-mini）
    private String model;
    // 聊天消息列表，包含对话上下文
    private List<OpenAiChatMessage> messages;
    // 采样温度，控制随机性：0=确定，1=最大随机
    private Double temperature;
    // 最大生成 token 数
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    // 是否使用流式响应
    private Boolean stream;
    // 可用工具列表（启用工具调用时使用）
    private List<OpenAiTool> tools;
    // 工具选择策略："auto"（自动）、"none"（禁用）、或指定工具
    @JsonProperty("tool_choice")
    private String toolChoice;
}
