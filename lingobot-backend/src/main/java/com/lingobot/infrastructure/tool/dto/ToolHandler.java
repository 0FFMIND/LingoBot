package com.lingobot.infrastructure.tool.dto;

import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;

import java.util.List;

import static com.lingobot.infrastructure.tool.dto.ToolMode.GENERAL_MODES;

/**
 * 工具处理器接口。
 *
 * 所有工具必须实现此接口，定义工具的基本行为规范。
 * 每个工具需要提供名称、定义、执行逻辑、所属类别和支持的模式，
 * 由 ToolRegistry 统一管理和调度。
 */
public interface ToolHandler {

    // 获取工具名称，返回工具唯一标识名称，需与 ToolDefinition.name 一致
    String getName();

    // 获取工具定义，返回工具的元数据定义，包含名称、描述、参数 schema 等
    ToolDefinition getToolDefinition();

    // 执行工具调用，接收工具调用请求（包含参数和上下文），返回工具执行结果
    ToolResult execute(ToolCall call);

    // 获取工具类别，返回工具所属的类别，用于控制不同模式下的可见性
    ToolCategory getCategory();

    // 获取支持的模式列表，返回支持的对话模式列表，返回 null 表示使用默认通用模式
    default List<String> getSupportedModes() {
        return null;
    }

    // 检查是否支持指定模式，接收对话模式标识，返回 true 表示支持该模式
    default boolean supportsMode(String mode) {
        List<String> supportedModes = getSupportedModes();

        if (supportedModes != null && !supportedModes.isEmpty()) {
            return supportedModes.contains(mode);
        }

        return GENERAL_MODES.contains(mode);
    }

    // 根据具体动作窄化 OpenAI 工具定义。默认不处理，由具体工具按需覆盖。
    default OpenAiTool narrowOpenAiTool(OpenAiTool tool, String action) {
        return tool;
    }
}
