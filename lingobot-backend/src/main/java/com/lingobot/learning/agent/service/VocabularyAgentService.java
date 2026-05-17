package com.lingobot.learning.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.learning.agent.dto.AgentPlanRequest;
import com.lingobot.learning.agent.dto.AgentPlanResponse;
import com.lingobot.learning.agent.dto.MemoryRecallPlan;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.service.LlmService;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyAgentService {

    private final LlmService llmService;
    private final VocabularyMemoryService vocabularyMemoryService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    public AgentPlanResponse planAndRecall(AgentPlanRequest request) {
        log.info("Agent planning memory recall for intent={}, userId={}", request.getIntent(), request.getUserId());

        Long conversationId = resolveConversationId(request);

        MemoryRecallPlan plan = generateMemoryPlan(request);

        log.info("Generated memory plan: intent={}, l1Recent={}, l1Wrong={}, l1Regenerated={}, l2Mastered={}, l2Reviewing={}, l2Learning={}, l2Weak={}",
                request.getIntent(),
                plan.getL1RecentLimit(), plan.getL1WrongLimit(), plan.getL1RegeneratedLimit(),
                plan.getL2MasteredLimit(), plan.getL2ReviewingLimit(), plan.getL2LearningLimit(), plan.getL2WeakLimit());
        log.info("Plan reasoning: {}", plan.getReasoning());

        VocabularyGenerationIntent intent = parseIntent(request.getIntent());
        VocabularyMemoryContext memoryContext = vocabularyMemoryService.retrieveMemoryWithLimits(
                request.getUserId(),
                conversationId,
                intent,
                plan.getL1RecentLimit(),
                plan.getL1WrongLimit(),
                plan.getL1RegeneratedLimit(),
                plan.getL2MasteredLimit(),
                plan.getL2ReviewingLimit(),
                plan.getL2LearningLimit(),
                plan.getL2WeakLimit()
        );

        return AgentPlanResponse.builder()
                .plan(plan)
                .memoryContext(memoryContext)
                .conversationId(conversationId)
                .build();
    }

    public MemoryRecallPlan generateMemoryPlan(AgentPlanRequest request) {
        String intent = request.getIntent() != null ? request.getIntent() : "next_word";

        MemoryRecallPlan heuristicPlan = getHeuristicPlan(intent);

        try {
            MemoryRecallPlan llmPlan = askLlmForPlan(request, heuristicPlan);
            return validateAndAdjustPlan(llmPlan, heuristicPlan);
        } catch (Exception e) {
            log.warn("LLM planning failed, using heuristic plan: {}", e.getMessage());
            return heuristicPlan;
        }
    }

    private MemoryRecallPlan getHeuristicPlan(String intent) {
        return switch (intent) {
            case "new_word" -> MemoryRecallPlan.builder()
                    .l1RecentLimit(10)
                    .l1WrongLimit(10)
                    .l1RegeneratedLimit(10)
                    .l2MasteredLimit(20)
                    .l2ReviewingLimit(20)
                    .l2LearningLimit(20)
                    .l2WeakLimit(20)
                    .reasoning("New word mode: need comprehensive exclusion list to avoid duplicates")
                    .build();

            case "review" -> MemoryRecallPlan.builder()
                    .l1RecentLimit(5)
                    .l1WrongLimit(15)
                    .l1RegeneratedLimit(5)
                    .l2MasteredLimit(0)
                    .l2ReviewingLimit(20)
                    .l2LearningLimit(10)
                    .l2WeakLimit(15)
                    .reasoning("Review mode: prioritize wrong words and reviewing words")
                    .build();

            case "hybrid" -> MemoryRecallPlan.builder()
                    .l1RecentLimit(8)
                    .l1WrongLimit(12)
                    .l1RegeneratedLimit(5)
                    .l2MasteredLimit(5)
                    .l2ReviewingLimit(15)
                    .l2LearningLimit(10)
                    .l2WeakLimit(10)
                    .reasoning("Hybrid mode: balance between review and new words")
                    .build();

            case "regenerate" -> MemoryRecallPlan.builder()
                    .l1RecentLimit(5)
                    .l1WrongLimit(5)
                    .l1RegeneratedLimit(15)
                    .l2MasteredLimit(5)
                    .l2ReviewingLimit(5)
                    .l2LearningLimit(5)
                    .l2WeakLimit(5)
                    .reasoning("Regenerate mode: check regenerated history to understand user preferences")
                    .build();

            default -> MemoryRecallPlan.builder()
                    .l1RecentLimit(DEFAULT_LIMIT)
                    .l1WrongLimit(DEFAULT_LIMIT)
                    .l1RegeneratedLimit(DEFAULT_LIMIT)
                    .l2MasteredLimit(DEFAULT_LIMIT)
                    .l2ReviewingLimit(DEFAULT_LIMIT)
                    .l2LearningLimit(DEFAULT_LIMIT)
                    .l2WeakLimit(DEFAULT_LIMIT)
                    .reasoning("Default mode: balanced memory retrieval")
                    .build();
        };
    }

    private MemoryRecallPlan askLlmForPlan(AgentPlanRequest request, MemoryRecallPlan heuristicPlan) throws Exception {
        List<OpenAiChatMessage> messages = new ArrayList<>();

        String systemPrompt = buildSystemPrompt();
        messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));

        String userPrompt = buildUserPrompt(request, heuristicPlan);
        messages.add(OpenAiChatMessage.createTextMessage("user", userPrompt));

        String response = llmService.chat(messages);
        log.debug("LLM plan response: {}", response);

        return parseLlmResponse(response, heuristicPlan);
    }

    private String buildSystemPrompt() {
        return """
            你是一个词汇学习记忆规划助手。根据用户的学习意图和当前状态，决定应该从各级记忆中抓取多少条记录。
            
            记忆层级说明：
            - L1_RECENT: 最近7天接触过的词（短期去重）
            - L1_WRONG: 最近14天答错的词（复习优先）
            - L1_REGENERATED: 最近14天重新生成过的词（用户不满意信号）
            - L2_MASTERED: 已掌握词（掌握度>=80%）
            - L2_REVIEWING: 复习中词
            - L2_LEARNING: 学习中词
            - L2_WEAK: 薄弱词（掌握度<40%，错误>=1）
            
            输出格式要求（严格JSON，不要其他内容）：
            {
              "l1RecentLimit": 数量,
              "l1WrongLimit": 数量,
              "l1RegeneratedLimit": 数量,
              "l2MasteredLimit": 数量,
              "l2ReviewingLimit": 数量,
              "l2LearningLimit": 数量,
              "l2WeakLimit": 数量,
              "reasoning": "简短说明为什么这样分配"
            }
            
            限制：每个数量范围 0-50，0表示不需要。
            """;
    }

    private String buildUserPrompt(AgentPlanRequest request, MemoryRecallPlan heuristicPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户学习意图: ").append(request.getIntent() != null ? request.getIntent() : "next_word").append("\n\n");

        if (request.getCurrentWord() != null && !request.getCurrentWord().isBlank()) {
            sb.append("当前学习的单词: ").append(request.getCurrentWord()).append("\n\n");
        }

        if (request.getUserMessage() != null && !request.getUserMessage().isBlank()) {
            sb.append("用户最新消息: ").append(request.getUserMessage()).append("\n\n");
        }

        sb.append("启发式建议的抓取数量:\n");
        sb.append("- L1_RECENT: ").append(heuristicPlan.getL1RecentLimit()).append("\n");
        sb.append("- L1_WRONG: ").append(heuristicPlan.getL1WrongLimit()).append("\n");
        sb.append("- L1_REGENERATED: ").append(heuristicPlan.getL1RegeneratedLimit()).append("\n");
        sb.append("- L2_MASTERED: ").append(heuristicPlan.getL2MasteredLimit()).append("\n");
        sb.append("- L2_REVIEWING: ").append(heuristicPlan.getL2ReviewingLimit()).append("\n");
        sb.append("- L2_LEARNING: ").append(heuristicPlan.getL2LearningLimit()).append("\n");
        sb.append("- L2_WEAK: ").append(heuristicPlan.getL2WeakLimit()).append("\n\n");
        sb.append("启发式理由: ").append(heuristicPlan.getReasoning()).append("\n\n");
        sb.append("请根据用户意图调整抓取数量，输出JSON格式的计划。");

        return sb.toString();
    }

    private MemoryRecallPlan parseLlmResponse(String response, MemoryRecallPlan fallback) {
        try {
            String jsonStr = extractJson(response);
            JsonNode node = objectMapper.readTree(jsonStr);

            return MemoryRecallPlan.builder()
                    .l1RecentLimit(node.path("l1RecentLimit").asInt(fallback.getL1RecentLimit()))
                    .l1WrongLimit(node.path("l1WrongLimit").asInt(fallback.getL1WrongLimit()))
                    .l1RegeneratedLimit(node.path("l1RegeneratedLimit").asInt(fallback.getL1RegeneratedLimit()))
                    .l2MasteredLimit(node.path("l2MasteredLimit").asInt(fallback.getL2MasteredLimit()))
                    .l2ReviewingLimit(node.path("l2ReviewingLimit").asInt(fallback.getL2ReviewingLimit()))
                    .l2LearningLimit(node.path("l2LearningLimit").asInt(fallback.getL2LearningLimit()))
                    .l2WeakLimit(node.path("l2WeakLimit").asInt(fallback.getL2WeakLimit()))
                    .reasoning(node.path("reasoning").asText(fallback.getReasoning()))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse LLM plan response: {}", e.getMessage());
            return fallback;
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response.trim();
    }

    private MemoryRecallPlan validateAndAdjustPlan(MemoryRecallPlan plan, MemoryRecallPlan fallback) {
        return MemoryRecallPlan.builder()
                .l1RecentLimit(clamp(plan.getL1RecentLimit(), 0, MAX_LIMIT, fallback.getL1RecentLimit()))
                .l1WrongLimit(clamp(plan.getL1WrongLimit(), 0, MAX_LIMIT, fallback.getL1WrongLimit()))
                .l1RegeneratedLimit(clamp(plan.getL1RegeneratedLimit(), 0, MAX_LIMIT, fallback.getL1RegeneratedLimit()))
                .l2MasteredLimit(clamp(plan.getL2MasteredLimit(), 0, MAX_LIMIT, fallback.getL2MasteredLimit()))
                .l2ReviewingLimit(clamp(plan.getL2ReviewingLimit(), 0, MAX_LIMIT, fallback.getL2ReviewingLimit()))
                .l2LearningLimit(clamp(plan.getL2LearningLimit(), 0, MAX_LIMIT, fallback.getL2LearningLimit()))
                .l2WeakLimit(clamp(plan.getL2WeakLimit(), 0, MAX_LIMIT, fallback.getL2WeakLimit()))
                .reasoning(plan.getReasoning() != null ? plan.getReasoning() : fallback.getReasoning())
                .build();
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private Long resolveConversationId(AgentPlanRequest request) {
        if (request.getConversationPublicId() != null) {
            return conversationService.resolvePublicIdToId(request.getConversationPublicId());
        }
        return null;
    }

    private VocabularyGenerationIntent parseIntent(String intent) {
        if (intent == null) return VocabularyGenerationIntent.NEXT_WORD;
        return switch (intent) {
            case "regenerate" -> VocabularyGenerationIntent.REGENERATE;
            case "new_word" -> VocabularyGenerationIntent.NEW_WORD;
            case "review" -> VocabularyGenerationIntent.REVIEW;
            case "hybrid" -> VocabularyGenerationIntent.HYBRID;
            default -> VocabularyGenerationIntent.NEXT_WORD;
        };
    }
}
