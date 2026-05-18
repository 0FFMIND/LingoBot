package com.lingobot.learning.vocabulary.graph;

import com.lingobot.infrastructure.common.config.ConversationProperties;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.vocabulary.dto.VocabularyBatchGenerationResult;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.graph.nodes.VocabularyBatchNodeActions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 批量单词卡片生成工作流。
 *
 * 使用 LangGraph 构建状态机工作流，一次性生成多张单词卡片。
 * 执行顺序：MEMORY_RECALL → PLANNING → GENERATION → VALIDATION → PERSISTENCE
 *
 * 工作流特点：
 * - VALIDATION 节点根据校验结果决定后续流向：
 *   校验通过 → PERSISTENCE（持久化）
 *   校验失败但未超过重试次数 → GENERATION（重新生成）
 *   校验失败且超过重试次数 → END（结束）
 * - 批量生成的卡片中，第一张默认已揭示（isRevealed=true），其余为隐藏状态
 * - execute 方法支持指定批量大小，未指定时使用配置中的默认值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyBatchGraph {

    private final VocabularyBatchNodeActions nodeActions;
    private final ConversationProperties conversationProperties;

    private CompiledGraph<VocabularyBatchState> compiledGraph;

    // Spring 启动时初始化并编译工作流图
    @PostConstruct
    public void init() throws GraphStateException {
        log.info("开始初始化 VocabularyBatchGraph 批量工作流...");

        AgentStateFactory<VocabularyBatchState> stateFactory = VocabularyBatchState::new;
        StateGraph<VocabularyBatchState> workflow = new StateGraph<>(stateFactory);

        // 注册工作流节点
        workflow.addNode("LIGHTWEIGHT_RECALL", nodeActions.lightweightRecall());
        workflow.addNode("AGENT_MEMORY_RECALL", nodeActions.agentMemoryRecall());
        workflow.addNode("PLANNING", nodeActions.planning());
        workflow.addNode("GENERATION", nodeActions.generation());
        workflow.addNode("VALIDATION", nodeActions.validation());
        workflow.addNode("PERSISTENCE", nodeActions.persistence());

        // 设置线性执行顺序
        workflow.addEdge(StateGraph.START, "LIGHTWEIGHT_RECALL");
        workflow.addEdge("LIGHTWEIGHT_RECALL", "AGENT_MEMORY_RECALL");
        workflow.addEdge("AGENT_MEMORY_RECALL", "PLANNING");
        workflow.addEdge("PLANNING", "GENERATION");
        workflow.addEdge("GENERATION", "VALIDATION");

        // 校验节点的条件分支映射
        Map<String, String> validationEdgeMappings = new HashMap<>();
        validationEdgeMappings.put("PERSISTENCE", "PERSISTENCE");
        validationEdgeMappings.put("GENERATION", "GENERATION");
        validationEdgeMappings.put("END", StateGraph.END);

        // 校验节点的条件分支逻辑
        workflow.addConditionalEdges(
                "VALIDATION",
                state -> CompletableFuture.supplyAsync(() -> {
                    if (state.isValid()) {
                        return "PERSISTENCE";
                    } else if (nodeActions.shouldRetry(state)) {
                        log.info("批量校验失败，重试生成中（第 {} 次）", state.getRetryCount());
                        return "GENERATION";
                    } else {
                        log.warn("已达最大重试次数，批量生成终止，返回空结果");
                        return "END";
                    }
                }),
                validationEdgeMappings
        );

        workflow.addEdge("PERSISTENCE", StateGraph.END);

        this.compiledGraph = workflow.compile();

        log.info("VocabularyBatchGraph 批量工作流初始化完成");
    }

    // 执行批量单词卡片生成（使用默认批量大小）
    public Optional<VocabularyBatchGenerationResult> execute(Long conversationId, Long userId, String category,
                                                              String difficulty, VocabularyGenerationIntent intent) {
        return execute(conversationId, userId, category, difficulty, intent, conversationProperties.getVocabularyDefaultBatchSize());
    }

    // 执行批量单词卡片生成（指定批量大小）
    public Optional<VocabularyBatchGenerationResult> execute(Long conversationId, Long userId, String category,
                                                              String difficulty, VocabularyGenerationIntent intent,
                                                              int batchSize) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("conversationId", conversationId);
            inputs.put("userId", userId);
            inputs.put("category", category);
            inputs.put("difficulty", difficulty);
            inputs.put("intent", intent);
            inputs.put("batchSize", batchSize);
            inputs.put("retryCount", 0);

            log.info("开始执行 VocabularyBatchGraph: conversationId={}, userId={}, category={}, difficulty={}, intent={}, batchSize={}",
                    conversationId, userId, category, difficulty, intent, batchSize);

            Optional<VocabularyBatchState> finalState = compiledGraph.invoke(inputs);

            if (finalState.isPresent() && finalState.get().getSavedCards() != null && !finalState.get().getSavedCards().isEmpty()) {
                VocabularyBatchState state = finalState.get();
                List<VocabularyCardDTO> allCards = state.getSavedCards();

                // 分离已揭示和未揭示的卡片
                List<VocabularyCardDTO> revealedCards = allCards.stream()
                        .filter(card -> Boolean.TRUE.equals(card.getIsRevealed()))
                        .collect(Collectors.toList());

                List<VocabularyCardDTO> hiddenCards = allCards.stream()
                        .filter(card -> !Boolean.TRUE.equals(card.getIsRevealed()))
                        .collect(Collectors.toList());

                VocabularyBatchGenerationResult result = VocabularyBatchGenerationResult.builder()
                        .revealedCards(revealedCards)
                        .hiddenCards(hiddenCards)
                        .totalRevealed(revealedCards.size())
                        .totalHidden(hiddenCards.size())
                        .totalCount(allCards.size())
                        .build();

                log.info("VocabularyBatchGraph 执行成功: total={}, revealed={}, hidden={}",
                        allCards.size(), revealedCards.size(), hiddenCards.size());
                return Optional.of(result);
            }

            log.warn("VocabularyBatchGraph 执行完成，但未生成有效卡片");
            return Optional.empty();

        } catch (Exception e) {
            log.error("VocabularyBatchGraph 执行失败", e);
            return Optional.empty();
        }
    }
}
