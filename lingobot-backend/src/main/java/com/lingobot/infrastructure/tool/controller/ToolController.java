package com.lingobot.infrastructure.tool.controller;

import com.lingobot.infrastructure.tool.dto.ToolCall;
import com.lingobot.infrastructure.tool.dto.ToolDefinition;
import com.lingobot.infrastructure.tool.dto.ToolResult;
import com.lingobot.infrastructure.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具 REST API 控制器。
 *
 * 提供工具列表查询、工具调用等接口，供前端或其他服务调用。
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    // 工具服务
    private final ToolService toolService;

    // 获取所有可用的工具列表，返回通用格式的工具定义列表
    @GetMapping
    public ResponseEntity<List<ToolDefinition>> listTools() {
        log.info("Listing all tools");
        List<ToolDefinition> tools = toolService.listTools();
        return ResponseEntity.ok(tools);
    }

    // 调用单个工具，接收工具调用请求，返回工具执行结果
    @PostMapping("/call")
    public ResponseEntity<ToolResult> callTool(@RequestBody ToolCall call) {
        log.info("Calling tool: {} with id: {}", call.getName(), call.getId());
        ToolResult result = toolService.callTool(call);
        return ResponseEntity.ok(result);
    }
}
