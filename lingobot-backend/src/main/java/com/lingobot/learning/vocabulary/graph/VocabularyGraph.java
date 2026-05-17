package com.lingobot.learning.vocabulary.graph;

import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.graph.nodes.VocabularyNodeActions;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyGraph {

    private final VocabularyNodeActions nodeActions;

    private CompiledGraph<VocabularyState> compiledGraph;

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing VocabularyGraph...");

        AgentStateFactory<VocabularyState> stateFactory = VocabularyState::new;
        StateGraph<VocabularyState> workflow = new StateGraph<>(stateFactory);

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
                        log.info("Validation failed, retrying generation (attempt {})", state.getRetryCount());
                        return "GENERATION";
                    } else {
                        log.warn("Max retries exceeded, falling back");
                        return "FALLBACK";
                    }
                }),
                validationEdgeMappings
        );

        workflow.addEdge("PERSISTENCE", StateGraph.END);
        workflow.addEdge("FALLBACK", StateGraph.END);

        this.compiledGraph = workflow.compile();

        log.info("VocabularyGraph initialized successfully");
    }

    public Optional<WordCardData> execute(Long conversationId, Long userId, String category,
                                           String difficulty, VocabularyGenerationIntent intent) {
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("conversationId", conversationId);
            inputs.put("userId", userId);
            inputs.put("category", category);
            inputs.put("difficulty", difficulty);
            inputs.put("intent", intent);
            inputs.put("retryCount", 0);

            log.info("Executing VocabularyGraph: conversationId={}, userId={}, category={}, difficulty={}, intent={}",
                    conversationId, userId, category, difficulty, intent);

            Optional<VocabularyState> finalState = compiledGraph.invoke(inputs);

            if (finalState.isPresent() && finalState.get().getSavedCard() != null) {
                VocabularyState state = finalState.get();
                WordCardData result = WordCardData.builder()
                        .word(state.getSavedCard().getWord())
                        .phonetic(state.getSavedCard().getPhonetic())
                        .partOfSpeech(state.getSavedCard().getPartOfSpeech())
                        .meaning(state.getSavedCard().getMeaning())
                        .example(state.getSavedCard().getExample())
                        .exampleTranslation(state.getSavedCard().getExampleTranslation())
                        .synonyms(state.getSavedCard().getSynonyms())
                        .category(state.getSavedCard().getCategory())
                        .difficulty(state.getSavedCard().getDifficulty())
                        .build();

                log.info("VocabularyGraph completed successfully: word={}", result.getWord());
                return Optional.of(result);
            }

            log.warn("VocabularyGraph completed without saved card");
            return Optional.empty();

        } catch (Exception e) {
            log.error("VocabularyGraph execution failed", e);
            return Optional.empty();
        }
    }
}
