package com.lingobot.infrastructure.tool.service;

import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolCategory;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolHandler;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具注册表。
 *
 * 管理所有注册的工具，提供工具注册、查询和执行功能。
 * 应用启动时通过 Spring 自动装配所有 ToolHandler 实现类并注册，
 * 运行时根据对话模式过滤可调用的工具，支持工具查找和执行调度。
 */
@Slf4j
@Service
public class ToolRegistry {

    // 工具处理器映射：工具名称 → 处理器实例
    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();

    // 工具定义列表：按注册顺序保存，供查询使用
    private final List<ToolDefinition> toolDefinitions = new ArrayList<>();

    // 初始化方法，应用启动时执行，打印注册日志
    @PostConstruct
    public void init() {
        log.info("Tool Registry initialized");
    }

    // 注册单个工具，将工具处理器加入映射表和定义列表
    public void registerTool(ToolHandler handler) {
        String toolName = handler.getName();
        toolHandlers.put(toolName, handler);
        toolDefinitions.add(handler.getToolDefinition());
        log.info("Registered tool: {} (category: {})", toolName, handler.getCategory());
    }

    // 批量注册工具，遍历列表逐个注册
    public void registerTools(List<ToolHandler> handlers) {
        for (ToolHandler handler : handlers) {
            registerTool(handler);
        }
    }

    // 获取所有注册的工具列表，返回工具定义的副本
    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(toolDefinitions);
    }

    // 根据名称获取工具处理器
    public ToolHandler getToolHandler(String name) {
        return toolHandlers.get(name);
    }

    // 根据模式获取工具列表，agent 模式返回全部，其他模式按类别和支持性过滤
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

    // 执行指定的工具调用，找不到工具返回错误结果，异常时捕获并返回错误
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
