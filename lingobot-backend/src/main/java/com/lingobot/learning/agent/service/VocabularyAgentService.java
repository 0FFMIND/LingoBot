package com.lingobot.learning.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.learning.agent.dto.AgentPlanRequest;
import com.lingobot.learning.agent.dto.AgentPlanResponse;
import com.lingobot.learning.agent.dto.MemoryRecallPlan;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.service.LlmService;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.prompt.vocabulary.VocabularyAgentPromptBuilder;
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
    private final VocabularyAgentPromptBuilder vocabularyAgentPromptBuilder;
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

        log.info("Generated memory plan: intent={}, recommendedIntent={}, l1Recent={}, l1Wrong={}, l1Regenerated={}, l2Mastered={}, l2Reviewing={}, l2Learning={}, l2Weak={}",
                request.getIntent(),
                plan.getRecommendedIntent(),
                plan.getL1RecentLimit(), plan.getL1WrongLimit(), plan.getL1RegeneratedLimit(),
                plan.getL2MasteredLimit(), plan.getL2ReviewingLimit(), plan.getL2LearningLimit(), plan.getL2WeakLimit());
        log.info("Plan reasoning: {}", plan.getReasoning());

        String effectiveIntent = plan.getRecommendedIntent() != null ? plan.getRecommendedIntent() : request.getIntent();
        VocabularyGenerationIntent intent = parseIntent(effectiveIntent);
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
     * - smart_recommend：智能推荐模式，全面分析用户学习数据，推荐最适合的内容
     * - hybrid：混合模式，平衡复习和新词
     * - default：默认模式，均衡抓取
     *
     * @param intent 学习意图
     * @return 启发式记忆抓取计划
     */
    private MemoryRecallPlan getHeuristicPlan(String intent) {
        return switch (intent) {
            case "new_word" -> MemoryRecallPlan.builder()
                    .recommendedIntent("new_word")
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
                    .recommendedIntent("review")
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
                    .recommendedIntent("hybrid")
                    .l1RecentLimit(8)
                    .l1WrongLimit(12)
                    .l1RegeneratedLimit(5)
                    .l2MasteredLimit(5)
                    .l2ReviewingLimit(15)
                    .l2LearningLimit(10)
                    .l2WeakLimit(10)
                    .reasoning("Hybrid mode: balance between review and new words")
                    .build();

            case "smart_recommend" -> MemoryRecallPlan.builder()
                    .recommendedIntent("hybrid")
                    .l1RecentLimit(DEFAULT_LIMIT)
                    .l1WrongLimit(DEFAULT_LIMIT)
                    .l1RegeneratedLimit(DEFAULT_LIMIT)
                    .l2MasteredLimit(DEFAULT_LIMIT)
                    .l2ReviewingLimit(DEFAULT_LIMIT)
                    .l2LearningLimit(DEFAULT_LIMIT)
                    .l2WeakLimit(DEFAULT_LIMIT)
                    .reasoning("Smart recommend mode: default to hybrid, LLM will adjust based on user state")
                    .build();

            default -> MemoryRecallPlan.builder()
                    .recommendedIntent("hybrid")
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

        String systemPrompt = "smart_recommend".equals(request.getIntent())
                ? vocabularyAgentPromptBuilder.buildSmartRecommendSystemPrompt()
                : vocabularyAgentPromptBuilder.buildNormalSystemPrompt();
        messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));

        String userPrompt = vocabularyAgentPromptBuilder.buildUserPrompt(
                request.getIntent(),
                request.getCurrentWord(),
                request.getUserMessage(),
                heuristicPlan,
                request.getLearningOverview(),
                request.getConversationOverview());
        messages.add(OpenAiChatMessage.createTextMessage("user", userPrompt));

        String response = llmService.chat(llmProperties.getModel(), messages);
        log.debug("LLM plan response: {}", response);

        return parseLlmResponse(response, heuristicPlan);
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
                    .recommendedIntent(node.path("recommendedIntent").asText(fallback.getRecommendedIntent()))
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
                .recommendedIntent(plan.getRecommendedIntent() != null ? plan.getRecommendedIntent() : fallback.getRecommendedIntent())
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
        if (request.getConversationId() != null) {
            return request.getConversationId();
        }
        if (request.getConversationPublicId() != null) {
            return conversationService.resolvePublicIdToId(request.getConversationPublicId());
        }
        return null;
    }

    /**
     * 将字符串意图转换为 VocabularyGenerationIntent 枚举。
     *
     * 支持的意图：new_word、review、hybrid、smart_recommend，其他值默认返回 SMART_RECOMMEND。
     *
     * @param intent 字符串形式的学习意图
     * @return 对应的枚举值
     */
    private VocabularyGenerationIntent parseIntent(String intent) {
        if (intent == null) return VocabularyGenerationIntent.SMART_RECOMMEND;
        return switch (intent) {
            case "new_word" -> VocabularyGenerationIntent.NEW_WORD;
            case "review" -> VocabularyGenerationIntent.REVIEW;
            case "hybrid" -> VocabularyGenerationIntent.HYBRID;
            case "smart_recommend" -> VocabularyGenerationIntent.SMART_RECOMMEND;
            default -> VocabularyGenerationIntent.SMART_RECOMMEND;
        };
    }
}
