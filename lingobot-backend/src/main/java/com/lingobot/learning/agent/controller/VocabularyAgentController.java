package com.lingobot.learning.agent.controller;

import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.agent.dto.AgentPlanRequest;
import com.lingobot.learning.agent.dto.AgentPlanResponse;
import com.lingobot.learning.agent.dto.MemoryRecallPlan;
import com.lingobot.learning.agent.service.VocabularyAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/agent/vocabulary")
@RequiredArgsConstructor
public class VocabularyAgentController {

    private final VocabularyAgentService vocabularyAgentService;

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<AgentPlanResponse>> planAndRecall(@RequestBody AgentPlanRequest request) {
        log.info("Received vocabulary agent plan request: intent={}, userId={}", request.getIntent(), request.getUserId());
        
        AgentPlanResponse response = vocabularyAgentService.planAndRecall(request);
        
        return ResponseEntity.ok(ApiResponse.success("记忆规划完成", response));
    }

    @PostMapping("/plan/only")
    public ResponseEntity<ApiResponse<MemoryRecallPlan>> planOnly(@RequestBody AgentPlanRequest request) {
        log.info("Received vocabulary agent plan-only request: intent={}, userId={}", request.getIntent(), request.getUserId());
        
        MemoryRecallPlan plan = vocabularyAgentService.generateMemoryPlan(request);
        
        return ResponseEntity.ok(ApiResponse.success("记忆规划生成完成", plan));
    }
}
