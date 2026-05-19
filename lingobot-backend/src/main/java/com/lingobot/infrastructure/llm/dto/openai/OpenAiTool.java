package com.lingobot.infrastructure.llm.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 工具定义 DTO。
 *
 * 对应 OpenAI Chat Completions API 中的 tools 字段，用于定义模型可调用的函数。
 * 结构遵循 JSON Schema 规范描述函数参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiTool {

    // 工具类型，目前仅支持 "function"
    private String type;
    // 函数定义
    private Function function;

    // 函数定义，包含函数名称、描述和参数规范
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        // 函数名称，模型调用时会使用此名称
        private String name;
        // 函数功能描述，帮助模型理解何时调用此函数
        private String description;
        // 参数定义（JSON Schema 格式）
        private Parameters parameters;
    }

    // 函数参数定义，遵循 JSON Schema 规范，描述参数的类型、属性和必填字段
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameters {
        // 根类型，通常为 "object"
        private String type;
        // 参数属性映射：key=参数名，value=属性定义
        private Map<String, Property> properties;
        // 必填参数名称列表
        private List<String> required;
    }

    // 参数属性定义，描述单个参数的类型、描述、可选值等
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Property {
        // 参数类型：string、number、integer、boolean、array、object 等
        private String type;
        // 参数描述，帮助模型理解参数含义
        private String description;
        // 枚举值列表（如果参数只能取特定值）
        private List<String> enums;
        // 数组元素类型定义（type=array 时使用）
        private Items items;
    }

    // 数组元素定义，当参数类型为 array 时，描述数组元素的类型
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Items {
        // 元素类型
        private String type;
        // 元素枚举值列表
        private List<String> enums;
    }
}
