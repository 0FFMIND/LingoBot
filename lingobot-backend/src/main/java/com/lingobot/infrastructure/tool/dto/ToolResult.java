package com.lingobot.infrastructure.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具执行结果 DTO。
 *
 * 封装工具执行后的返回结果，包含成功标志、内容列表或错误信息。
 * 结果格式需统一，便于上层服务统一处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {

    /**
     * 调用标识，与 ToolCall.id 对应，用于匹配请求与响应。
     */
    private String id;

    /**
     * 工具名称，与被调用的工具名称一致。
     */
    private String name;

    /**
     * 执行是否成功。
     */
    private boolean success;

    /**
     * 执行结果内容列表，支持多段内容返回。
     * 成功时此字段非空。
     */
    private List<Content> content;

    /**
     * 错误信息，执行失败时此字段非空。
     */
    private String error;

    /**
     * 内容项定义。
     *
     * 支持多种内容类型（如 text、image 等），
     * 当前主要使用 text 类型返回 JSON 格式的数据。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String type;
        private String text;
    }
}
