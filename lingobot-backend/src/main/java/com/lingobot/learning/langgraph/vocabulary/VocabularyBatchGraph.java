package com.lingobot.learning.langgraph.vocabulary;

import com.lingobot.learning.langgraph.vocabulary.nodes.VocabularyBatchNodeActions;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.vocabulary.dto.VocabularyBatchGenerationResult;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyBatchGraph {

    private final VocabularyBatchNodeActions nodeActions;

    private CompiledGraph<VocabularyBatchState> compiledGraph;

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing VocabularyBatchGraph...");

        AgentStateFactory<VocabularyBatchState> stateFactory = VocabularyBatchState::new;
        StateGraph<VocabularyBatchState> workflow = new StateGraph<>(stateFactory);

        workflow.addNode("MEMORY_RECALL", nodeActions.memoryRecall());
        workflow.addNode("PLANNING", nodeActions.planning());
        workflow.addNode("GENERATION", nodeActions.generation());
        workflow.addNode("VALIDATION", nodeActions.validation());
        workflow.addNode("PERSISTENCE", nodeActions.persistence());
        workflow.addNode("FALLBACK", nodeActions.fallback());

        workflow.addEdge(StateGraph.START, "MEMORY_RECALL");
        workflow.addEdge("MEMORY_RECALL", "PLANNING");
        workflow.addEdge("PLANNING", "GENERATION");
        workflow.addEdge("GENERATION", "VALIDATION");

        Map<String, String> validationEdgeMappings = new HashMap<>();
        validationEdgeMappings.put("PERSISTENCE", "PERSISTENCE");
        validationEdgeMappings.put("GENERATION", "GENERATION");
        validationEdgeMappings.put("FALLBACK", "FALLBACK");

        workflow.addConditionalEdges(
                "VALIDATION",
                state -> CompletableFuture.supplyAsync(() -> {
                    if (state.isValid()) {
                        return "PERSISTENCE";
                    } else if (nodeActions.shouldRetry(state)) {
                        log.info("Batch validation failed, retrying generation (attempt {})", state.getRetryCount());
                        return "GENERATION";
                    } else {
                        log.warn("Max retries exceeded for batch generation, falling back");
                        return "FALLBACK";
                    }
                }),
                validationEdgeMappings
        );

        workflow.addEdge("PERSISTENCE", StateGraph.END);
        workflow.addEdge("FALLBACK", StateGraph.END);

        this.compiledGraph = workflow.compile();

        log.info("VocabularyBatchGraph initialized successfully");
    }

    public Optional<VocabularyBatchGenerationResult> execute(Long conversationId, Long userId, String category,
                                                              String difficulty, VocabularyGenerationIntent intent) {
        return execute(conversationId, userId, category, difficulty, intent, 10);
    }

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

            log.info("Executing VocabularyBatchGraph: conversationId={}, userId={}, category={}, difficulty={}, intent={}, batchSize={}",
                    conversationId, userId, category, difficulty, intent, batchSize);

            Optional<VocabularyBatchState> finalState = compiledGraph.invoke(inputs);

            if (finalState.isPresent() && finalState.get().getSavedCards() != null && !finalState.get().getSavedCards().isEmpty()) {
                VocabularyBatchState state = finalState.get();
                List<VocabularyCardDTO> allCards = state.getSavedCards();

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

                log.info("VocabularyBatchGraph completed successfully: total={}, revealed={}, hidden={}",
                        allCards.size(), revealedCards.size(), hiddenCards.size());
                return Optional.of(result);
            }

            log.warn("VocabularyBatchGraph completed without saved cards");
            return Optional.empty();

        } catch (Exception e) {
            log.error("VocabularyBatchGraph execution failed", e);
            return Optional.empty();
        }
    }
}
