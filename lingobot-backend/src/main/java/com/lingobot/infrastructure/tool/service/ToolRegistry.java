package com.lingobot.infrastructure.tool.service;

import com.lingobot.infrastructure.tool.ToolCategory;
import com.lingobot.infrastructure.tool.ToolHandler;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 工具注册表
// 管理所有注册的工具，提供工具注册、查询和执行功能
@Slf4j
@Service
public class ToolRegistry {

    // 工具处理器映射：工具名称 → 处理器实例
    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();

    // 工具定义列表：按注册顺序保存，供查询使用
    private final List<ToolDefinition> toolDefinitions = new ArrayList<>();

    // 初始化方法，应用启动时执行
    @PostConstruct
    public void init() {
        log.info("Tool Registry initialized");
    }

    // 注册单个工具
    public void registerTool(ToolHandler handler) {
        String toolName = handler.getName();
        toolHandlers.put(toolName, handler);
        toolDefinitions.add(handler.getToolDefinition());
        log.info("Registered tool: {} (category: {})", toolName, handler.getCategory());
    }

    // 批量注册工具
    public void registerTools(List<ToolHandler> handlers) {
        for (ToolHandler handler : handlers) {
            registerTool(handler);
        }
    }

    // 获取所有注册的工具列表
    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(toolDefinitions);
    }

    // 根据模式获取工具列表
    public List<ToolDefinition> getToolsForMode(String mode) {
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

    // 根据模式获取工具处理器列表
    public List<ToolHandler> getToolHandlersForMode(String mode) {
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

    // 根据名称获取工具定义
    public ToolDefinition getTool(String name) {
        ToolHandler handler = toolHandlers.get(name);
        return handler != null ? handler.getToolDefinition() : null;
    }

    // 检查是否存在指定名称的工具
    public boolean hasTool(String name) {
        return toolHandlers.containsKey(name);
    }

    // 执行指定的工具调用
    public ToolResult executeTool(ToolCall call) {
        String toolName = call.getName();
        ToolHandler handler = toolHandlers.get(toolName);

        if (handler == null) {
            log.error("Tool not found: {}", toolName);
            return ToolResult.builder()
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
            return ToolResult.builder()
                    .id(call.getId())
                    .name(toolName)
                    .success(false)
                    .error("Tool execution error: " + e.getMessage())
                    .build();
        }
    }
}
