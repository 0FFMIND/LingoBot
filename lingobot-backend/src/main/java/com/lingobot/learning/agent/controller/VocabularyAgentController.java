package com.lingobot.learning.agent.controller;

import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.agent.dto.AgentPlanRequest;
import com.lingobot.learning.agent.dto.AgentPlanResponse;
import com.lingobot.learning.agent.service.VocabularyAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 词汇 Agent 控制器。
 *
 * 提供词汇学习相关的智能规划接口，对外暴露 /api/agent/vocabulary 端点。
 * 目前仅提供 planAndRecall 接口：
 * - 接收用户学习意图和上下文
 * - 调用 Service 层完成记忆规划和实际记忆检索
 * - 返回完整的规划结果和记忆上下文供下游使用
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/vocabulary")
@RequiredArgsConstructor
public class VocabularyAgentController {

    private final VocabularyAgentService vocabularyAgentService;

    // 执行记忆规划并检索记忆上下文：解析请求 → 生成抓取计划 → 检索记忆数据 → 返回结果
    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<AgentPlanResponse>> planAndRecall(@RequestBody AgentPlanRequest request) {
        log.info("Received vocabulary agent plan request: intent={}, userId={}", request.getIntent(), request.getUserId());
        
        AgentPlanResponse response = vocabularyAgentService.planAndRecall(request);
        
        return ResponseEntity.ok(ApiResponse.success("记忆规划完成", response));
    }
}
