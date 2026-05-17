package com.lingobot.learning.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.config.LlmProperties;
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

/**
 * 词汇 Agent 服务类。
 *
 * 核心职责是根据用户学习意图进行智能记忆规划：
 * - 先通过启发式规则生成默认的记忆抓取计划
 * - 再调用 LLM 根据用户上下文动态调整计划
 * - 最后根据最终计划从记忆库中检索实际数据
 *
 * 设计采用 "启发式兜底 + LLM 优化" 的两层策略：
 * 当 LLM 调用失败或返回无效结果时，自动回退到启发式计划，保证服务可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyAgentService {

    private final LlmService llmService;
    private final LlmProperties llmProperties;
    private final VocabularyMemoryService vocabularyMemoryService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    /**
     * 各级记忆的默认抓取数量。
     */
    private static final int DEFAULT_LIMIT = 10;

    /**
     * 各级记忆的最大抓取数量限制，防止 LLM 返回过大值。
     */
    private static final int MAX_LIMIT = 50;

    /**
     * 执行完整的记忆规划流程：生成计划 + 检索记忆。
     *
     * 流程：
     * 1. 解析会话公开 ID 为内部会话 ID
     * 2. 生成记忆抓取计划（启发式 + LLM 优化）
     * 3. 根据计划从记忆库检索各级记忆数据
     * 4. 组装响应返回
     *
     * @param request 规划请求，包含会话、用户、学习意图等信息
     * @return 完整的规划响应，包含抓取计划和实际记忆上下文
     */
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

    /**
     * 生成记忆抓取计划。
     *
     * 采用两层策略：
     * 1. 先根据学习意图生成启发式计划（兜底方案）
     * 2. 尝试调用 LLM 根据用户上下文动态调整计划
     * 3. 对 LLM 返回结果进行校验和修正，确保在合理范围内
     * 4. 若 LLM 调用失败，直接返回启发式计划
     *
     * @param request 规划请求
     * @return 记忆抓取计划
     */
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

    /**
     * 根据学习意图生成启发式记忆抓取计划。
     *
     * 支持的意图：
     * - new_word：新词模式，需要全面的去重列表
     * - review：复习模式，优先答错和复习中的词
     * - hybrid：混合模式，平衡复习和新词
     * - default：默认模式，均衡抓取
     *
     * @param intent 学习意图
     * @return 启发式记忆抓取计划
     */
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

    /**
     * 调用 LLM 根据用户上下文动态调整记忆抓取计划。
     *
     * 构建系统提示词（记忆层级说明 + 输出格式要求）和用户提示词
     * （意图、当前单词、用户消息、启发式建议），发送给 LLM 生成优化后的计划。
     *
     * @param request 规划请求
     * @param heuristicPlan 启发式计划，作为 LLM 的参考基准
     * @return LLM 生成的记忆抓取计划
     * @throws Exception LLM 调用或解析失败时抛出
     */
    private MemoryRecallPlan askLlmForPlan(AgentPlanRequest request, MemoryRecallPlan heuristicPlan) throws Exception {
        List<OpenAiChatMessage> messages = new ArrayList<>();

        String systemPrompt = buildSystemPrompt();
        messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));

        String userPrompt = buildUserPrompt(request, heuristicPlan);
        messages.add(OpenAiChatMessage.createTextMessage("user", userPrompt));

        String response = llmService.chat(llmProperties.getModel(), messages);
        log.debug("LLM plan response: {}", response);

        return parseLlmResponse(response, heuristicPlan);
    }

    /**
     * 构建 LLM 系统提示词。
     *
     * 包含：
     * - 角色定义（词汇学习记忆规划助手）
     * - 各记忆层级的详细说明
     * - 严格的 JSON 输出格式要求
     * - 数值范围限制（0-50）
     *
     * @return 系统提示词
     */
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

    /**
     * 构建 LLM 用户提示词。
     *
     * 包含：
     * - 用户学习意图
     * - 当前学习的单词（可选）
     * - 用户最新消息（可选）
     * - 启发式建议的抓取数量和理由
     *
     * @param request 规划请求
     * @param heuristicPlan 启发式计划
     * @return 用户提示词
     */
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

    /**
     * 解析 LLM 返回的 JSON 响应为 MemoryRecallPlan。
     *
     * 容错处理：
     * - 提取响应中的 JSON 部分（忽略前后的额外文本）
     * - 每个字段解析失败时使用启发式计划的对应值作为兜底
     *
     * @param response LLM 返回的原始响应
     * @param fallback 解析失败时的兜底计划
     * @return 解析后的记忆抓取计划
     */
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

    /**
     * 从 LLM 响应中提取 JSON 字符串。
     *
     * 找到第一个 '{' 和最后一个 '}' 的位置，截取中间内容。
     * 如果找不到有效的 JSON 结构，则返回原响应并去除首尾空白。
     *
     * @param response LLM 返回的原始响应
     * @return 提取出的 JSON 字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response.trim();
    }

    /**
     * 校验并调整 LLM 生成的计划，确保所有数值在合理范围内。
     *
     * 对每个抓取数量进行范围校验（0 到 MAX_LIMIT），超出范围则使用启发式计划的对应值。
     *
     * @param plan LLM 生成的计划
     * @param fallback 校验失败时的兜底计划
     * @return 校验调整后的计划
     */
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

    /**
     * 数值范围限制工具方法。
     *
     * 如果 value 在 [min, max] 范围内则返回 value，否则返回 fallback。
     *
     * @param value 待校验的值
     * @param min 最小值
     * @param max 最大值
     * @param fallback 超出范围时的兜底值
     * @return 校验后的值
     */
    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    /**
     * 解析会话公开 ID 为内部会话 ID。
     *
     * 如果请求中未提供 conversationPublicId，则返回 null，
     * 表示记忆检索不限制会话范围。
     *
     * @param request 规划请求
     * @return 内部会话 ID，可能为 null
     */
    private Long resolveConversationId(AgentPlanRequest request) {
        if (request.getConversationPublicId() != null) {
            return conversationService.resolvePublicIdToId(request.getConversationPublicId());
        }
        return null;
    }

    /**
     * 将字符串意图转换为 VocabularyGenerationIntent 枚举。
     *
     * 支持的意图：new_word、review、hybrid，其他值默认返回 NEXT_WORD。
     *
     * @param intent 字符串形式的学习意图
     * @return 对应的枚举值
     */
    private VocabularyGenerationIntent parseIntent(String intent) {
        if (intent == null) return VocabularyGenerationIntent.NEXT_WORD;
        return switch (intent) {
            case "new_word" -> VocabularyGenerationIntent.NEW_WORD;
            case "review" -> VocabularyGenerationIntent.REVIEW;
            case "hybrid" -> VocabularyGenerationIntent.HYBRID;
            default -> VocabularyGenerationIntent.NEXT_WORD;
        };
    }
}
