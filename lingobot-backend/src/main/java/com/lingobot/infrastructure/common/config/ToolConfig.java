package com.lingobot.infrastructure.common.config;

import com.lingobot.infrastructure.tool.adapter.VocabularyToolAdapter;
import com.lingobot.infrastructure.tool.service.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

// 工具配置类
// 应用启动时自动注册内置的工具到 ToolRegistry 中
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ToolConfig {

    // 工具注册表，用于管理和提供 AI 可调用的工具
    private final ToolRegistry toolRegistry;

    // 词汇学习工具适配器，提供单词卡展示、释义检查等功能
    private final VocabularyToolAdapter vocabularyToolAdapter;

    // 应用启动完成后执行，注册所有内置工具
    @PostConstruct
    public void init() {
        log.info("Registering built-in tools...");

        toolRegistry.registerTool(vocabularyToolAdapter);

        log.info("Registered {} built-in tools", toolRegistry.getAllTools().size());
    }
}
