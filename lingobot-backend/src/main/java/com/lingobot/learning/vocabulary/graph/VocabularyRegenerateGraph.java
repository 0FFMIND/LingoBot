package com.lingobot.learning.vocabulary.graph;

import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.graph.nodes.VocabularyRegenerateNodeActions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 单词卡片重新生成工作流。
 *
 * 使用 LangGraph 构建状态机工作流，依次执行以下节点：
 * MEMORY_RECALL → PLANNING → GENERATION → VALIDATION
 *
 * 注意：此工作流仅用于重新生成场景，不负责持久化。
 * 持久化由上层 VocabularyCardService 处理。
 *
 * 工作流特点：
 * - VALIDATION 节点根据校验结果决定后续流向：
 *   校验通过 → END（结束，返回生成结果）
 *   校验失败但未超过重试次数 → GENERATION（重新生成）
 *   校验失败且超过重试次数 → END（结束）
 * - @PostConstruct 在 Spring 启动时编译工作流图
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyRegenerateGraph {

    private final VocabularyRegenerateNodeActions nodeActions;

    private CompiledGraph<VocabularyState> compiledGraph;

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing VocabularyRegenerateGraph...");

        AgentStateFactory<VocabularyState> stateFactory = VocabularyState::new;
        StateGraph<VocabularyState> workflow = new StateGraph<>(stateFactory);

        workflow.addNode("MEMORY_RECALL", nodeActions.memoryRecall());
        workflow.addNode("PLANNING", nodeActions.planning());
        workflow.addNode("GENERATION", nodeActions.generation());
        workflow.addNode("VALIDATION", nodeActions.validation());

        workflow.addEdge(StateGraph.START, "MEMORY_RECALL");
        workflow.addEdge("MEMORY_RECALL", "PLANNING");
        workflow.addEdge("PLANNING", "GENERATION");
        workflow.addEdge("GENERATION", "VALIDATION");

        Map<String, String> validationEdgeMappings = new HashMap<>();
        validationEdgeMappings.put("SUCCESS", StateGraph.END);
        validationEdgeMappings.put("GENERATION", "GENERATION");
        validationEdgeMappings.put("END", StateGraph.END);

        workflow.addConditionalEdges(
                "VALIDATION",
                state -> CompletableFuture.supplyAsync(() -> {
                    if (state.isValid()) {
                        return "SUCCESS";
                    } else if (nodeActions.shouldRetry(state)) {
                        log.info("Regenerate validation failed, retrying generation (attempt {})", state.getRetryCount());
                        return "GENERATION";
                    } else {
                        log.error("Max retries exceeded for regenerate, aborting");
                        return "END";
                    }
                }),
                validationEdgeMappings
        );

        this.compiledGraph = workflow.compile();

        log.info("VocabularyRegenerateGraph initialized successfully");
    }

    public Optional<WordCardData> execute(Long conversationId, Long userId, String category,
                                           String difficulty, Integer regeneratePosition, String oldWord,
                                           String oldPartOfSpeech, String oldMeaning) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("conversationId", conversationId);
            inputs.put("userId", userId);
            inputs.put("category", category);
            inputs.put("difficulty", difficulty);
            inputs.put("intent", VocabularyGenerationIntent.REGENERATE);
            inputs.put("retryCount", 0);
            inputs.put("regeneratePosition", regeneratePosition);
            inputs.put("regenerateOldWord", oldWord);
            inputs.put("regenerateOldPartOfSpeech", oldPartOfSpeech);
            inputs.put("regenerateOldMeaning", oldMeaning);

            log.info("Executing VocabularyRegenerateGraph: conversationId={}, userId={}, category={}, difficulty={}, position={}",
                    conversationId, userId, category, difficulty, regeneratePosition);

            Optional<VocabularyState> finalState = compiledGraph.invoke(inputs);

            if (finalState.isPresent()) {
                VocabularyState state = finalState.get();

                if (state.getGeneratedCard() != null && state.isValid()) {
                    WordCardData result = state.getGeneratedCard();
                    log.info("VocabularyRegenerateGraph completed successfully: word={}", result.getWord());
                    return Optional.of(result);
                }
            }

            log.warn("VocabularyRegenerateGraph completed without valid generated card");
            return Optional.empty();

        } catch (Exception e) {
            log.error("VocabularyRegenerateGraph execution failed", e);
            return Optional.empty();
        }
    }
}
