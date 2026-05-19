package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 聊天响应 DTO。
 *
 * 对应 OpenAI Chat Completions API 的响应体，包含生成的消息、使用统计等信息。
 * 使用 @JsonAlias 注解同时支持 snake_case 和 camelCase 两种字段命名格式，
 * 提高与不同 API 提供商的兼容性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatResponse {

    // 响应唯一标识
    private String id;
    // 对象类型，通常为 "chat.completion"
    private String object;
    // 创建时间戳（秒）
    private Long created;
    // 使用的模型名称
    private String model;
    // 生成的候选回复列表
    private List<Choice> choices;
    // token 使用统计
    private Usage usage;

    // 候选回复，包含生成的消息、索引位置和结束原因
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        // 候选索引
        private Integer index;
        // 生成的消息
        private OpenAiChatMessage message;
        // 结束原因：stop（正常结束）、length（达到最大长度）、tool_calls（工具调用）等
        @JsonProperty("finish_reason")
        @JsonAlias("finishReason")
        private String finishReason;
    }

    // Token 使用统计，记录请求和响应消耗的 token 数量，用于计费
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        // 输入（提示词）消耗的 token 数
        @JsonProperty("prompt_tokens")
        @JsonAlias("promptTokens")
        private Integer promptTokens;
        // 输出（生成内容）消耗的 token 数
        @JsonProperty("completion_tokens")
        @JsonAlias("completionTokens")
        private Integer completionTokens;
        // 总消耗 token 数
        @JsonProperty("total_tokens")
        @JsonAlias("totalTokens")
        private Integer totalTokens;
    }
}
