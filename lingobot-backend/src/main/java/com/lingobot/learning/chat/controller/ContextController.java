package com.lingobot.learning.chat.controller;

import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("context", "compact"));
        Long transactionId = balanceService.freezeBalance(cost, "context", "compact", "AI压缩对话历史", conversationId);

        try {
            int beforeTokens = contextManagerService.getContextStatus(conversationId).getCurrentTokens();

            String summary = buildVocabularySummary(conversationId);
            conversation.setCompactedSummary(summary);
            conversation.setCompactedCount(
                    conversation.getCompactedCount() == null ? 1 : conversation.getCompactedCount() + 1
            );
            conversation.setLastCompactedAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            int afterTokens = summary.length() / 4; // rough estimate
            int savedTokens = Math.max(0, beforeTokens - afterTokens);

            balanceService.confirmTransaction(transactionId);
            log.info("Compact completed: publicId={}, savedTokens={}", publicId, savedTokens);

            Map<String, Object> result = new HashMap<>();
            result.put("executed", true);
            result.put("beforeTokens", beforeTokens);
            result.put("afterTokens", afterTokens);
            result.put("savedTokens", savedTokens);
            result.put("compactBatch", conversation.getCompactedCount());
            return ResponseEntity.ok(ApiResponse.success("压缩成功", result));

        } catch (Exception e) {
            balanceService.cancelTransaction(transactionId);
            log.error("Compact failed: publicId={}", publicId, e);
            throw e;
        }
    }

    private String buildVocabularySummary(Long conversationId) {
        List<VocabularyCard> cards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        if (cards.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (VocabularyCard card : cards) {
            sb.append("- ").append(card.getWord());
            if (card.getPhonetic() != null) sb.append(" [").append(card.getPhonetic()).append("]");
            if (card.getMeaning() != null) sb.append("：").append(card.getMeaning());
            if (Boolean.TRUE.equals(card.getMeaningIsCorrect())) {
                sb.append("（释义✓）");
            } else if (Boolean.FALSE.equals(card.getMeaningIsCorrect())) {
                sb.append("（释义✗）");
            }
            if (Boolean.TRUE.equals(card.getIsCompleted())) sb.append("（已完成）");
            sb.append("\n");
        }
        return sb.toString();
    }
}
