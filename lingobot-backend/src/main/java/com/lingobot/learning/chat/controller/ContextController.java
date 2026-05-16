package com.lingobot.learning.chat.controller;

import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.service.LlmService;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextManagerService contextManagerService;
    private final ConversationRepository conversationRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final BalanceService balanceService;
    private final ApiConfigProperties apiConfigProperties;
    private final ConversationService conversationService;
    private final LlmService llmService;

    @GetMapping("/status/{publicId}")
    public ResponseEntity<ApiResponse<Object>> getContextStatus(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        return ResponseEntity.ok(ApiResponse.success(contextManagerService.getContextStatus(conversationId)));
    }

    @PostMapping("/compact/{publicId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compact(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ChatException.badRequest("对话不存在: " + publicId));

        List<VocabularyCard> allCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        List<VocabularyCard> completedCards = allCards.stream()
                .filter(card -> Boolean.TRUE.equals(card.getIsCompleted()))
                .toList();

        int recentCardsToKeep = 3;
        if (completedCards.size() <= recentCardsToKeep) {
            Map<String, Object> result = new HashMap<>();
            result.put("executed", false);
            result.put("reason", String.format("至少需要 %d 张已完成的单词卡片才能压缩，当前只有 %d 张",
                    recentCardsToKeep + 1, completedCards.size()));
            return ResponseEntity.ok(ApiResponse.success("暂不需要压缩", result));
        }

        List<VocabularyCard> cardsToCompact = completedCards.subList(0, completedCards.size() - recentCardsToKeep);
        List<VocabularyCard> recentCards = completedCards.subList(
                completedCards.size() - recentCardsToKeep,
                completedCards.size()
        );

        if (cardsToCompact.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("executed", false);
            result.put("reason", "没有可压缩的单词卡片");
            return ResponseEntity.ok(ApiResponse.success("暂不需要压缩", result));
        }

        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("context", "compact"));
        Long transactionId = balanceService.freezeBalance(cost, "context", "compact", "AI压缩对话历史", conversationId);

        try {
            int completedCardsBeforeTokens = calculateCompletedCardsTokens(completedCards);

            String compactedSummary = callLlmToCompact(cardsToCompact, conversation.getVocabularyCompactedSummary());
            int summaryTokens = compactedSummary.length() / 4;
            int recentCardsTokens = calculateCompletedCardsTokens(recentCards);
            int afterTokens = summaryTokens + recentCardsTokens;
            int savedTokens = Math.max(0, completedCardsBeforeTokens - afterTokens);

            conversation.setVocabularyCompactedSummary(compactedSummary);
            conversation.setVocabularyCompactedCardCount(
                    conversation.getVocabularyCompactedCardCount() == null
                            ? cardsToCompact.size()
                            : conversation.getVocabularyCompactedCardCount() + cardsToCompact.size()
            );
            conversation.setVocabularyLastCompactedAt(LocalDateTime.now());
            VocabularyCard lastCompactedCard = cardsToCompact.get(cardsToCompact.size() - 1);
            conversation.setVocabularyLastCompactedCardId(lastCompactedCard.getId());
            conversation.setVocabularyLastCompactedPosition(lastCompactedCard.getPosition());
            conversationRepository.save(conversation);

            balanceService.confirmTransaction(transactionId);
            log.info("Compact completed: publicId={}, compactedCards={}, recentCards={}, beforeTokens={}, afterTokens={}, savedTokens={}",
                    publicId, cardsToCompact.size(), recentCards.size(), completedCardsBeforeTokens, afterTokens, savedTokens);

            Map<String, Object> result = new HashMap<>();
            result.put("executed", true);
            result.put("beforeTokens", completedCardsBeforeTokens);
            result.put("afterTokens", afterTokens);
            result.put("savedTokens", savedTokens);
            result.put("compactedCardsCount", cardsToCompact.size());
            result.put("recentCardsCount", recentCards.size());
            result.put("totalCompactedCards", conversation.getVocabularyCompactedCardCount());
            return ResponseEntity.ok(ApiResponse.success("压缩成功", result));

        } catch (Exception e) {
            balanceService.cancelTransaction(transactionId);
            log.error("Compact failed: publicId={}", publicId, e);
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
        messages.add(OpenAiChatMessage.createTextMessage("system", buildCompactSystemPrompt()));
        messages.add(OpenAiChatMessage.createTextMessage("user", buildCompactUserPrompt(vocabularyHistory, existingSummary)));

        log.info("Calling LLM to compact {} vocabulary cards", cardsToCompact.size());
        String compactedSummary = llmService.chat(messages);
        log.info("LLM compact completed, summary length: {}", compactedSummary.length());

        return compactedSummary;
    }

    private String buildCompactSystemPrompt() {
        return """
                你是 LingoBot 的词汇学习记忆压缩器。你的任务是把较长的词汇学习 context history 压缩成后续生成词卡时可直接使用的记忆摘要。

                压缩目标：
                - 大幅减少 token。
                - 保留会影响后续生成、避重和复习决策的信息。
                - 不编造原始 context history 里没有的信息。
                - 如果输入里包含旧摘要，请把新历史合并进去，输出一份完整的新摘要，而不是只总结新增部分。

                必须保留：
                - 单词、词性、音标、核心中文释义。
                - 用户是否答对、答错、重新生成、不满意。
                - 用户明显薄弱的点，例如释义误解、造句语法问题、搭配不自然。
                - 应避免短期重复生成的词。

                可以压缩或省略：
                - 例句全文，除非用户错误必须依赖该句才能理解。
                - 冗长 AI 反馈，改写成一句短的错误/掌握情况。
                - 重复的说明性文字。

                输出要求：
                - 只输出摘要正文，不要解释你的压缩过程。
                - 使用中文。
                - 使用 Markdown。
                - 没有内容的分区可以省略。

                推荐结构：
                ## 词汇学习历史摘要
                ### 已掌握/较熟悉
                - word [phonetic] (pos.)：中文释义；

                ### 薄弱/需要复习
                - word [phonetic] (pos.)：中文释义；

                ### 重新生成/不满意信号
                - word [phonetic] (pos.)：中文释义；

                """;
    }

    private String buildCompactUserPrompt(String vocabularyHistory, String existingSummary) {
        StringBuilder sb = new StringBuilder();

        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            sb.append("## 已有压缩摘要\n");
            sb.append(existingSummary);
            sb.append("\n\n");
        }

        sb.append("## 本次需要压缩的 context history\n");
        sb.append(vocabularyHistory);
        sb.append("\n\n");

        sb.append("请根据上面的 context history 输出新的完整压缩摘要。");
        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            sb.append("注意：必须把已有摘要和本次新历史合并，不能丢失旧摘要中的有效记忆。");
        } else {
            sb.append("请生成一份新的压缩摘要。");
        }
        sb.append("只输出摘要正文。");

        return sb.toString();
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
