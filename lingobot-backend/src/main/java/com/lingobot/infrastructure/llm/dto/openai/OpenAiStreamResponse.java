package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 流式响应 DTO。
 *
 * 对应 OpenAI Chat Completions API 流式模式下的单次响应（data: 行）。
 * 与非流式响应的主要区别在于使用 delta 而非 message，增量返回内容。
 * 注意：当前 LlmService 内部直接解析 JSON 而未使用此 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiStreamResponse {

    // 响应唯一标识
    private String id;
    // 对象类型，通常为 "chat.completion.chunk"
    private String object;
    // 创建时间戳（秒）
    private Long created;
    // 使用的模型名称
    private String model;
    // 增量响应候选列表
    private List<StreamChoice> choices;

    // 流式候选回复，包含增量内容和结束原因
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamChoice {
        // 候选索引
        private Integer index;
        // 增量内容
        private Delta delta;
        // 结束原因（流结束时非空）
        private String finishReason;
    }

    // 增量内容，每次流式响应只包含部分内容，需要客户端拼接
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        // 消息角色（通常只在第一次响应中出现）
        private String role;
        // 增量文本内容
        private String content;
    }
}
