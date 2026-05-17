package com.lingobot.infrastructure.mcp.controller;

import com.lingobot.infrastructure.mcp.dto.McpTool;
import com.lingobot.infrastructure.mcp.dto.McpToolCall;
import com.lingobot.infrastructure.mcp.dto.McpToolResult;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.mcp.service.McpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 工具 REST API 控制器 * 提供工具列表查询、工具调用等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    /**
     * 获取所有可用的 MCP 工具列表
     */
    @GetMapping("/tools")
    public ResponseEntity<List<McpTool>> listTools() {
        log.info("Listing all MCP tools");
        List<McpTool> tools = mcpService.listTools();
        return ResponseEntity.ok(tools);
    }

    /**
     * 获取 OpenAI 格式的工具列表     * 用于与OpenAI API 兼容的工具调用     */
    @GetMapping("/tools/openai")
    public ResponseEntity<List<OpenAiTool>> listOpenAiTools() {
        log.info("Listing MCP tools in OpenAI format");
        List<OpenAiTool> tools = mcpService.getOpenAiTools();
        return ResponseEntity.ok(tools);
    }

    /**
     * 调用单个 MCP 工具
     */
    @PostMapping("/tools/call")
    public ResponseEntity<McpToolResult> callTool(@RequestBody McpToolCall call) {
        log.info("Calling tool: {} with id: {}", call.getName(), call.getId());
        McpToolResult result = mcpService.callTool(call);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量调用多个 MCP 工具
     */
    @PostMapping("/tools/batch-call")
    public ResponseEntity<List<McpToolResult>> batchCallTools(@RequestBody List<McpToolCall> calls) {
        log.info("Batch calling {} tools", calls.size());
        List<McpToolResult> results = calls.stream()
                .map(mcpService::callTool)
                .toList();
        return ResponseEntity.ok(results);
    }
}
