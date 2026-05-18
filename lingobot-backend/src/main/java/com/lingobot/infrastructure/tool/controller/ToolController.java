package com.lingobot.infrastructure.tool.controller;

import com.lingobot.infrastructure.llm.dto.openai.OpenAiTool;
import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.lingobot.infrastructure.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 工具 REST API 控制器
// 提供工具列表查询、工具调用等接口，供前端或其他服务调用
@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    // 获取所有可用的工具列表
    @GetMapping
    public ResponseEntity<List<ToolDefinition>> listTools() {
        log.info("Listing all tools");
        List<ToolDefinition> tools = toolService.listTools();
        return ResponseEntity.ok(tools);
    }

    // 获取 OpenAI 格式的工具列表
    @GetMapping("/openai")
    public ResponseEntity<List<OpenAiTool>> listOpenAiTools() {
        log.info("Listing tools in OpenAI format");
        List<OpenAiTool> tools = toolService.getOpenAiTools();
        return ResponseEntity.ok(tools);
    }

    // 调用单个工具
    @PostMapping("/call")
    public ResponseEntity<ToolResult> callTool(@RequestBody ToolCall call) {
        log.info("Calling tool: {} with id: {}", call.getName(), call.getId());
        ToolResult result = toolService.callTool(call);
        return ResponseEntity.ok(result);
    }

    // 批量调用多个工具
    @PostMapping("/batch-call")
    public ResponseEntity<List<ToolResult>> batchCallTools(@RequestBody List<ToolCall> calls) {
        log.info("Batch calling {} tools", calls.size());
        List<ToolResult> results = calls.stream()
                .map(toolService::callTool)
                .toList();
        return ResponseEntity.ok(results);
    }
}
