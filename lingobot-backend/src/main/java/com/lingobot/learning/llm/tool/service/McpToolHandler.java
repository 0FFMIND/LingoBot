package com.lingobot.learning.llm.tool.service;

import com.lingobot.learning.llm.tool.dto.McpTool;
import com.lingobot.learning.llm.tool.dto.McpToolCall;
import com.lingobot.learning.llm.tool.dto.McpToolResult;

import java.util.List;

import static com.lingobot.learning.llm.tool.service.ToolMode.GENERAL_MODES;

/**
 * MCP 工具处理器接口
 * 所有 MCP 工具都需要实现此接口，定义工具的基本行为
 */
public interface McpToolHandler {
    
    /**
     * 获取工具名称
     */
    String getName();
    
    /**
     * 获取工具定义（描述工具的参数和功能）
     */
    McpTool getToolDefinition();
    
    /**
     * 执行工具调用
     */
    McpToolResult execute(McpToolCall call);
    
    /**
     * 获取工具类别
     */
    ToolCategory getCategory();
    
    /**
     * 获取支持的模式列表
     */
    default List<String> getSupportedModes() {
        return null;
    }
    
    /**
     * 检查是否支持指定模式
     */
    default boolean supportsMode(String mode) {
        List<String> supportedModes = getSupportedModes();
        
        if (supportedModes != null && !supportedModes.isEmpty()) {
            return supportedModes.contains(mode);
        }
        
        return GENERAL_MODES.contains(mode);
    }
}
