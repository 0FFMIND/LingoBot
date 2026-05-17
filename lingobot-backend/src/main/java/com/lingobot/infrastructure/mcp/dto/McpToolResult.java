package com.lingobot.infrastructure.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 工具执行结果 DTO
 * 包含工具执行的成功失败状态、返回内容或错误信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolResult {

    private String id;
    private String name;
    private boolean success;
    private List<Content> content;
    private String error;

    /**
     * 工具返回的内容     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String type;
        private String text;
        private Object data;
    }
}
