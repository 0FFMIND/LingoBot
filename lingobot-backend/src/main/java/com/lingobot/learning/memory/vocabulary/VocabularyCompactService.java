package com.lingobot.learning.memory.vocabulary;

import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.learning.chat.dto.HistoryBuildRequest;
import com.lingobot.learning.chat.service.MessageHistoryService;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.repository.VocabularyConversationDataRepository;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.service.LlmService;
import com.lingobot.learning.prompt.vocabulary.VocabularyCompactPromptBuilder;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCompactService {

    private final VocabularyConversationDataRepository learningDataRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final BalanceService balanceService;
    private final ApiConfigProperties apiConfigProperties;
    private final LlmService llmService;
    private final LlmProperties llmProperties;
    private final VocabularyCompactPromptBuilder vocabularyCompactPromptBuilder;
    private final VocabularyMemoryPromptBuilder vocabularyMemoryPromptBuilder;
    private final MessageHistoryService messageHistoryService;

    private static final int RECENT_CARDS_TO_KEEP = 3;

    @Transactional
    public VocabularyCompactResult executeCompact(Long conversationId) {
        VocabularyConversationData learningData = learningDataRepository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());

        List<VocabularyCard> allCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        List<VocabularyCard> completedCards = allCards.stream()
                .filter(card -> Boolean.TRUE.equals(card.getIsCompleted()))
                .toList();

        if (completedCards.size() <= RECENT_CARDS_TO_KEEP) {
            return VocabularyCompactResult.notExecuted(String.format("至少需要 %d 张已完成的单词卡片才能压缩，当前只有 %d 张",
                    RECENT_CARDS_TO_KEEP + 1, completedCards.size()));
        }

        List<VocabularyCard> cardsToCompact = completedCards.subList(0, completedCards.size() - RECENT_CARDS_TO_KEEP);
        List<VocabularyCard> recentCards = completedCards.subList(
                completedCards.size() - RECENT_CARDS_TO_KEEP,
                completedCards.size()
        );

        if (cardsToCompact.isEmpty()) {
            return VocabularyCompactResult.notExecuted("没有可压缩的单词卡片");
        }

        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("context", "compact"));
        Long transactionId = balanceService.freezeBalance(cost, "context", "compact", "AI压缩对话历史", conversationId);

        try {
            int beforeCharacters = messageHistoryService.calculateContextLength(
                    HistoryBuildRequest.forConversation(conversationId, "vocabulary"));
            int beforeTokens = beforeCharacters / 4;

            String compactedSummary = callLlmToCompact(cardsToCompact, learningData.getVocabularyCompactedSummary());

            learningData.setVocabularyCompactedSummary(compactedSummary);
            learningData.setVocabularyCompactedCardCount(
                    learningData.getVocabularyCompactedCardCount() == null
                            ? cardsToCompact.size()
                            : learningData.getVocabularyCompactedCardCount() + cardsToCompact.size()
            );
            learningData.setVocabularyLastCompactedAt(LocalDateTime.now());
            VocabularyCard lastCompactedCard = cardsToCompact.get(cardsToCompact.size() - 1);
            learningData.setVocabularyLastCompactedCardId(lastCompactedCard.getId());
            learningData.setVocabularyLastCompactedPosition(lastCompactedCard.getPosition());
            learningDataRepository.save(learningData);

            int afterCharacters = messageHistoryService.calculateContextLength(
                    HistoryBuildRequest.forConversation(conversationId, "vocabulary"));
            int afterTokens = afterCharacters / 4;
            int savedTokens = Math.max(0, beforeTokens - afterTokens);

            balanceService.confirmTransaction(transactionId);
            log.info("Compact completed: conversationId={}, compactedCards={}, recentCards={}, beforeTokens={}, afterTokens={}, savedTokens={}",
                    conversationId, cardsToCompact.size(), recentCards.size(), beforeTokens, afterTokens, savedTokens);

            return new VocabularyCompactResult(
                    true,
                    "vocabulary 压缩成功",
                    beforeTokens,
                    afterTokens,
                    savedTokens,
                    cardsToCompact.size(),
                    recentCards.size(),
                    learningData.getVocabularyCompactedCardCount()
            );

        } catch (Exception e) {
            balanceService.cancelTransaction(transactionId);
            log.error("Compact failed: conversationId={}", conversationId, e);
            throw e;
        }
    }

    private String callLlmToCompact(List<VocabularyCard> cardsToCompact, String existingSummary) {
        if (cardsToCompact == null || cardsToCompact.isEmpty()) {
            return existingSummary != null ? existingSummary : "";
        }

        List<VocabularyMemoryRecord> records = cardsToCompact.stream()
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
        String vocabularyHistory = vocabularyMemoryPromptBuilder.formatConversationCards(records);

        List<OpenAiChatMessage> messages = new ArrayList<>();
        messages.add(OpenAiChatMessage.createTextMessage("system", vocabularyCompactPromptBuilder.buildCompactSystemPrompt()));
        messages.add(OpenAiChatMessage.createTextMessage("user", vocabularyCompactPromptBuilder.buildCompactUserPrompt(vocabularyHistory, existingSummary)));

        log.info("Calling LLM to compact {} vocabulary cards", cardsToCompact.size());
        String compactedSummary = llmService.chat(llmProperties.getModel(), messages);
        log.info("LLM compact completed, summary length: {}", compactedSummary.length());

        return compactedSummary;
    }

    private VocabularyMemoryRecord toMemoryRecord(VocabularyCard card) {
        VocabularyMemoryEventType eventType = Boolean.TRUE.equals(card.getIsRegenerated())
                ? VocabularyMemoryEventType.REGENERATED
                : VocabularyMemoryEventType.SEEN;

        String userAnswer = null;
        String aiFeedback = null;
        VocabularyMemoryInteractionType interactionType = VocabularyMemoryInteractionType.NONE;
        String meaningCheckUserAnswer = null;
        String meaningCheckAiFeedback = null;
        Boolean meaningCheckIsCorrect = null;
        String sentenceAnalysisUserAnswer = null;
        String sentenceAnalysisAiFeedback = null;
        Boolean sentenceAnalysisIsCorrect = null;

        boolean hasMeaningCheck = Boolean.TRUE.equals(card.getMeaningCheckCompleted());
        boolean hasSentenceAnalysis = Boolean.TRUE.equals(card.getSentenceAnalysisCompleted());

        if (hasMeaningCheck) {
            if (card.getUserMeaningGuess() != null && !card.getUserMeaningGuess().isEmpty()) {
                meaningCheckUserAnswer = card.getUserMeaningGuess();
            }
            if (card.getMeaningCheckResult() != null && !card.getMeaningCheckResult().isEmpty()) {
                meaningCheckAiFeedback = card.getMeaningCheckResult();
            }
            meaningCheckIsCorrect = card.getMeaningIsCorrect();
            if (card.getMeaningIsCorrect() != null) {
                eventType = card.getMeaningIsCorrect()
                        ? VocabularyMemoryEventType.CORRECT
                        : VocabularyMemoryEventType.WRONG;
            }
            userAnswer = meaningCheckUserAnswer;
            aiFeedback = meaningCheckAiFeedback;
            interactionType = VocabularyMemoryInteractionType.MEANING_CHECK;
        }

        if (hasSentenceAnalysis) {
            if (card.getUserEnglishSentence() != null && !card.getUserEnglishSentence().isEmpty()) {
                sentenceAnalysisUserAnswer = card.getUserEnglishSentence();
            }
            if (card.getSentenceAnalysis() != null && !card.getSentenceAnalysis().isEmpty()) {
                sentenceAnalysisAiFeedback = card.getSentenceAnalysis();
            }
            sentenceAnalysisIsCorrect = card.getSentenceMeaningMatches();
            if (card.getSentenceMeaningMatches() != null && !hasMeaningCheck) {
                eventType = card.getSentenceMeaningMatches()
                        ? VocabularyMemoryEventType.CORRECT
                        : VocabularyMemoryEventType.WRONG;
            }
            if (!hasMeaningCheck) {
                userAnswer = sentenceAnalysisUserAnswer;
                aiFeedback = sentenceAnalysisAiFeedback;
                interactionType = VocabularyMemoryInteractionType.SENTENCE_ANALYSIS;
            }
        }

        return VocabularyMemoryRecord.builder()
                .word(card.getWord())
                .meaning(card.getMeaning())
                .partOfSpeech(card.getPartOfSpeech())
                .reviewCount(0)
                .isMastered(false)
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .position(card.getPosition())
                .regenerationIndex(card.getRegenerationIndex())
                .isRegenerated(card.getIsRegenerated())
                .userAnswer(userAnswer)
                .aiFeedback(aiFeedback)
                .interactionType(interactionType)
                .meaningCheckUserAnswer(meaningCheckUserAnswer)
                .meaningCheckAiFeedback(meaningCheckAiFeedback)
                .meaningCheckIsCorrect(meaningCheckIsCorrect)
                .sentenceAnalysisUserAnswer(sentenceAnalysisUserAnswer)
                .sentenceAnalysisAiFeedback(sentenceAnalysisAiFeedback)
                .sentenceAnalysisIsCorrect(sentenceAnalysisIsCorrect)
                .build();
    }
}
