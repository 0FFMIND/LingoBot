package com.lingobot.mcp.service;

import com.lingobot.mcp.dto.McpTool;
import com.lingobot.mcp.dto.McpToolCall;
import com.lingobot.mcp.dto.McpToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具注册�? * 管理所有注册的 MCP 工具，提供工具注册、查询和执行功能
 */
@Slf4j
@Service
public class McpToolRegistry {

    private final Map<String, McpToolHandler> toolHandlers = new HashMap<>();
    private final List<McpTool> toolDefinitions = new ArrayList<>();

    @PostConstruct
    public void init() {
        log.info("MCP Tool Registry initialized");
    }

    /**
     * 注册单个 MCP 工具
     */
    public void registerTool(McpToolHandler handler) {
        String toolName = handler.getName();
        toolHandlers.put(toolName, handler);
        toolDefinitions.add(handler.getToolDefinition());
        log.info("Registered MCP tool: {} (category: {})", toolName, handler.getCategory());
    }

    /**
     * 批量注册 MCP 工具
     */
    public void registerTools(List<McpToolHandler> handlers) {
        for (McpToolHandler handler : handlers) {
            registerTool(handler);
        }
    }

    /**
     * 获取所有注册的工具列表
     */
    public List<McpTool> getAllTools() {
        return new ArrayList<>(toolDefinitions);
    }

    /**
     * 根据模式获取工具列表
     */
    public List<McpTool> getToolsForMode(String mode) {
        if ("agent".equals(mode)) {
            return getAllTools();
        } else {
            return toolHandlers.entrySet().stream()
                    .filter(entry -> ToolCategory.ONE_TIME.equals(entry.getValue().getCategory())
                            || ToolCategory.ALL.equals(entry.getValue().getCategory()))
                    .filter(entry -> entry.getValue().supportsMode(mode))
                    .map(entry -> entry.getValue().getToolDefinition())
                    .collect(Collectors.toList());
        }
    }

    /**
     * 根据模式获取工具处理器列�?     */
    public List<McpToolHandler> getToolHandlersForMode(String mode) {
        if ("agent".equals(mode)) {
            return new ArrayList<>(toolHandlers.values());
        } else {
            return toolHandlers.values().stream()
                    .filter(handler -> ToolCategory.ONE_TIME.equals(handler.getCategory())
                            || ToolCategory.ALL.equals(handler.getCategory()))
                    .filter(handler -> handler.supportsMode(mode))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 根据名称获取工具定义
     */
    public McpTool getTool(String name) {
        McpToolHandler handler = toolHandlers.get(name);
        return handler != null ? handler.getToolDefinition() : null;
    }

    /**
     * 检查是否存在指定名称的工具
     */
    public boolean hasTool(String name) {
        return toolHandlers.containsKey(name);
    }

    /**
     * 执行指定的工具调�?     */
    public McpToolResult executeTool(McpToolCall call) {
        String toolName = call.getName();
        McpToolHandler handler = toolHandlers.get(toolName);
        
        if (handler == null) {
            log.error("Tool not found: {}", toolName);
            return McpToolResult.builder()
                    .id(call.getId())
                    .name(toolName)
                    .success(false)
                    .error("Tool not found: " + toolName)
                    .build();
        }
        
        try {
            log.info("Executing tool: {} with id: {}", toolName, call.getId());
            return handler.execute(call);
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return McpToolResult.builder()
                    .id(call.getId())
                    .name(toolName)
                    .success(false)
                    .error("Tool execution error: " + e.getMessage())
                    .build();
        }
    }
}
