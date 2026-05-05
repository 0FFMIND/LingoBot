package com.lingobot.learning.llm.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具定义 DTO
 * 描述工具的名称、描述和参数定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpTool {

    private String name;
    private String description;
    private ToolArguments arguments;

    /**
     * 工具参数定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolArguments {
        private String type;
        private Map<String, Property> properties;
        private List<String> required;
    }

    /**
     * 参数属性定义     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {
        private String type;
        private String description;
        private List<String> enums;
        private Items items;
    }

    /**
     * 数组类型参数的元素定义     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Items {
        private String type;
        private List<String> enums;
    }
}
