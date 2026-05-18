package com.lingobot.infrastructure.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具定义 DTO。
 *
 * 描述工具的名称、描述和参数定义，用于告知 AI 模型如何调用此工具。
 * AI 模型根据此 schema 生成正确的工具调用参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private String name;
    private String description;
    private ToolArguments arguments;

    /**
     * 工具参数定义。
     *
     * 采用 JSON Schema 风格描述参数结构，
     * 包含参数类型、属性定义和必填字段列表。
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
     * 参数属性定义。
     *
     * 描述单个参数的类型、说明、枚举值（如果有），
     * 数组类型还需包含元素定义。
     */
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
     * 数组类型参数的元素定义。
     *
     * 当参数类型为 array 时使用，描述数组元素的类型和枚举约束。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Items {
        private String type;
        private List<String> enums;
    }
}
