package com.lingobot.infrastructure.common.config;

import com.lingobot.learning.llm.tool.service.McpToolRegistry;
import com.lingobot.learning.llm.tool.tools.VocabularyTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * MCP 工具配置类。
 * 应用启动时自动注册内置的 LLM 工具到 McpToolRegistry 中，
 * 使 AI 模型在对话过程中可以调用这些工具。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolConfig {

    // MCP 工具注册表，用于管理和提供 LLM 可调用的工具
    private final McpToolRegistry toolRegistry;
    
    // 内置的词汇工具，提供单词查询等功能
    private final VocabularyTool vocabularyTool;

    // 应用启动完成后执行，注册所有内置 MCP 工具
    @PostConstruct
    public void init() {
        log.info("Registering built-in MCP tools...");
        
        toolRegistry.registerTool(vocabularyTool);
        
        log.info("Registered {} built-in MCP tools", toolRegistry.getAllTools().size());
    }
}
