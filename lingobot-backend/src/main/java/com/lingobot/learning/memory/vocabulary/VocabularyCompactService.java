package com.lingobot.learning.memory.vocabulary;

import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.config.LlmProperties;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final int RECENT_CARDS_TO_KEEP = 3;

    @Transactional
    public Map<String, Object> executeCompact(Long conversationId) {
        VocabularyConversationData learningData = learningDataRepository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());

        List<VocabularyCard> allCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        List<VocabularyCard> completedCards = allCards.stream()
                .filter(card -> Boolean.TRUE.equals(card.getIsCompleted()))
                .toList();

        if (completedCards.size() <= RECENT_CARDS_TO_KEEP) {
            Map<String, Object> result = new HashMap<>();
            result.put("executed", false);
            result.put("reason", String.format("至少需要 %d 张已完成的单词卡片才能压缩，当前只有 %d 张",
                    RECENT_CARDS_TO_KEEP + 1, completedCards.size()));
            return result;
        }

        List<VocabularyCard> cardsToCompact = completedCards.subList(0, completedCards.size() - RECENT_CARDS_TO_KEEP);
        List<VocabularyCard> recentCards = completedCards.subList(
                completedCards.size() - RECENT_CARDS_TO_KEEP,
                completedCards.size()
        );

        if (cardsToCompact.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("executed", false);
            result.put("reason", "没有可压缩的单词卡片");
            return result;
        }

        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("context", "compact"));
        Long transactionId = balanceService.freezeBalance(cost, "context", "compact", "AI压缩对话历史", conversationId);

        try {
            int completedCardsBeforeTokens = calculateCompletedCardsTokens(completedCards);

            String compactedSummary = callLlmToCompact(cardsToCompact, learningData.getVocabularyCompactedSummary());
            int summaryTokens = compactedSummary.length() / 4;
            int recentCardsTokens = calculateCompletedCardsTokens(recentCards);
            int afterTokens = summaryTokens + recentCardsTokens;
            int savedTokens = Math.max(0, completedCardsBeforeTokens - afterTokens);

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

            balanceService.confirmTransaction(transactionId);
            log.info("Compact completed: conversationId={}, compactedCards={}, recentCards={}, beforeTokens={}, afterTokens={}, savedTokens={}",
                    conversationId, cardsToCompact.size(), recentCards.size(), completedCardsBeforeTokens, afterTokens, savedTokens);

            Map<String, Object> result = new HashMap<>();
            result.put("executed", true);
            result.put("beforeTokens", completedCardsBeforeTokens);
            result.put("afterTokens", afterTokens);
            result.put("savedTokens", savedTokens);
            result.put("compactedCardsCount", cardsToCompact.size());
            result.put("recentCardsCount", recentCards.size());
            result.put("totalCompactedCards", learningData.getVocabularyCompactedCardCount());
            return result;

        } catch (Exception e) {
            balanceService.cancelTransaction(transactionId);
            log.error("Compact failed: conversationId={}", conversationId, e);
            throw e;
        }
    }

    private int calculateCompletedCardsTokens(List<VocabularyCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int totalTokens = 0;
        for (VocabularyCard card : cards) {
            StringBuilder sb = new StringBuilder();
            appendValue(sb, card.getWord());
            appendValue(sb, card.getMeaning());
            appendValue(sb, card.getPhonetic());
            appendValue(sb, card.getPartOfSpeech());
            appendValue(sb, card.getExample());
            appendValue(sb, card.getExampleTranslation());
            appendValue(sb, card.getUserMeaningGuess());
            appendValue(sb, card.getMeaningCheckResult());
            appendValue(sb, card.getUserEnglishSentence());
            appendValue(sb, card.getSentenceAnalysis());
            totalTokens += sb.length() / 4;
        }
        return totalTokens;
    }

    private String callLlmToCompact(List<VocabularyCard> cardsToCompact, String existingSummary) {
        if (cardsToCompact == null || cardsToCompact.isEmpty()) {
            return existingSummary != null ? existingSummary : "";
        }

        String vocabularyHistory = buildVocabularyHistory(cardsToCompact);

        List<OpenAiChatMessage> messages = new ArrayList<>();
        messages.add(OpenAiChatMessage.createTextMessage("system", vocabularyCompactPromptBuilder.buildCompactSystemPrompt()));
        messages.add(OpenAiChatMessage.createTextMessage("user", vocabularyCompactPromptBuilder.buildCompactUserPrompt(vocabularyHistory, existingSummary)));

        log.info("Calling LLM to compact {} vocabulary cards", cardsToCompact.size());
        String compactedSummary = llmService.chat(llmProperties.getModel(), messages);
        log.info("LLM compact completed, summary length: {}", compactedSummary.length());

        return compactedSummary;
    }

    private String buildVocabularyHistory(List<VocabularyCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (VocabularyCard card : cards) {
            sb.append("### 词卡 ").append(card.getPosition() != null ? card.getPosition() + 1 : "")
                    .append(": ").append(card.getWord() != null ? card.getWord() : "").append("\n");

            appendLine(sb, "音标", card.getPhonetic());
            appendLine(sb, "词性", card.getPartOfSpeech());
            appendLine(sb, "释义", card.getMeaning());
            appendLine(sb, "类别", card.getCategory());
            appendLine(sb, "难度", card.getDifficulty());
            appendLine(sb, "状态", Boolean.TRUE.equals(card.getIsCompleted()) ? "已完成" : "未完成");

            if (Boolean.TRUE.equals(card.getIsRegenerated())) {
                sb.append("- 重新生成：是，表示用户对该词卡不满意或已替换\n");
            }

            appendLine(sb, "释义检查用户答案", card.getUserMeaningGuess());
            if (card.getMeaningIsCorrect() != null) {
                appendLine(sb, "释义检查结果", Boolean.TRUE.equals(card.getMeaningIsCorrect()) ? "正确" : "错误");
            }
            appendLine(sb, "释义检查反馈", card.getMeaningCheckResult());

            appendLine(sb, "造句用户答案", card.getUserEnglishSentence());
            if (card.getSentenceMeaningMatches() != null) {
                appendLine(sb, "造句语义结果", Boolean.TRUE.equals(card.getSentenceMeaningMatches()) ? "匹配" : "不匹配");
            }
            appendLine(sb, "造句反馈", card.getSentenceAnalysis());

            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendValue(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value);
        }
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append("：").append(value).append("\n");
        }
    }
}
