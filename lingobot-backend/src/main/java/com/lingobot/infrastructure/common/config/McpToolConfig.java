package com.lingobot.infrastructure.common.config;

import com.lingobot.learning.llm.tool.service.McpToolRegistry;
import com.lingobot.learning.llm.tool.tools.VocabularyTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolConfig {

    private final McpToolRegistry toolRegistry;
    private final VocabularyTool vocabularyTool;

    @PostConstruct
    public void init() {
        log.info("Registering built-in MCP tools...");
        
        toolRegistry.registerTool(vocabularyTool);
        
        log.info("Registered {} built-in MCP tools", toolRegistry.getAllTools().size());
    }
}
