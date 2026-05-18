package com.lingobot.infrastructure.tool;

import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolResult;

import java.util.List;

import static com.lingobot.infrastructure.tool.ToolMode.GENERAL_MODES;

// 工具处理器接口
// 所有工具必须实现此接口，定义工具的基本行为规范
public interface ToolHandler {

    // 获取工具名称
    String getName();

    // 获取工具定义
    ToolDefinition getToolDefinition();

    // 执行工具调用
    ToolResult execute(ToolCall call);

    // 获取工具类别
    ToolCategory getCategory();

    // 获取支持的模式列表
    default List<String> getSupportedModes() {
        return null;
    }

    // 检查是否支持指定模式
    default boolean supportsMode(String mode) {
        List<String> supportedModes = getSupportedModes();

        if (supportedModes != null && !supportedModes.isEmpty()) {
            return supportedModes.contains(mode);
        }

        return GENERAL_MODES.contains(mode);
    }
}
