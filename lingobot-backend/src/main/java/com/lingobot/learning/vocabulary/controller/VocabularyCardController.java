package com.lingobot.learning.vocabulary.controller;

import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.service.VocabularyCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词汇卡管理控制器
 * 提供词汇卡的增删改查、导航、AI生成等功能 */
@Slf4j
@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyCardController {

    private final VocabularyCardService vocabularyCardService;
    private final BalanceService balanceService;
    private final ApiConfigProperties apiConfigProperties;

    /**
     * 创建新的词汇卡
     * @param conversationId 对话ID
     * @param request 词汇卡创建请求
     * @return 创建成功的词汇卡
     */
    @PostMapping("/cards")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> createCard(
            @RequestParam Long conversationId,
            @RequestBody CreateVocabularyCardRequest request) {
        VocabularyCardDTO created = vocabularyCardService.createCard(conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("词汇卡创建成功", created));
    }

    /**
     * 根据ID获取词汇卡
     * @param cardId 词汇卡ID
     * @return 词汇卡详情
     */
    @GetMapping("/cards/{cardId}")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getCardById(@PathVariable Long cardId) {
        VocabularyCardDTO card = vocabularyCardService.getCardById(cardId);
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    /**
     * 获取对话的所有词汇卡
     * @param conversationId 对话ID
     * @return 词汇卡列表
     */
    @GetMapping("/conversations/{conversationId}/cards")
    public ResponseEntity<ApiResponse<List<VocabularyCardDTO>>> getAllCards(
            @PathVariable Long conversationId) {
        List<VocabularyCardDTO> cards = vocabularyCardService.getAllCards(conversationId);
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    /**
     * 获取当前正在学习的词汇卡（第一个未完成的，或最后一个）
     * @param conversationId 对话ID
     * @return 当前词汇卡
     */
    @GetMapping("/conversations/{conversationId}/current")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getCurrentCard(
            @PathVariable Long conversationId) {
        VocabularyCardDTO card = vocabularyCardService.getCurrentCard(conversationId);
        if (card == null) {
            return ResponseEntity.ok(ApiResponse.success("该对话没有词汇卡", null));
        }
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    /**
     * 获取下一个词汇卡
     * @param conversationId 对话ID
     * @param currentPosition 当前位置（可选）
     * @return 下一个词汇卡
     */
    @GetMapping("/conversations/{conversationId}/next")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getNextCard(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Integer currentPosition) {
        try {
            VocabularyCardDTO card = vocabularyCardService.getNextCard(conversationId, currentPosition);
            return ResponseEntity.ok(ApiResponse.success(card));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(e.getMessage(), null));
        }
    }

    /**
     * 获取上一个词汇卡
     * @param conversationId 对话ID
     * @param currentPosition 当前位置（可选）
     * @return 上一个词汇卡
     */
    @GetMapping("/conversations/{conversationId}/prev")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getPrevCard(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Integer currentPosition) {
        try {
            VocabularyCardDTO card = vocabularyCardService.getPrevCard(conversationId, currentPosition);
            return ResponseEntity.ok(ApiResponse.success(card));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(e.getMessage(), null));
        }
    }

    /**
     * 生成下一个词汇卡（通过AI生成新单词）
     * @param conversationId 对话ID
     * @param request 包含难度级别
     * @return 生成的词汇卡
     */
    @PostMapping("/conversations/{conversationId}/generate")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> generateNextCard(
            @PathVariable Long conversationId,
            @RequestBody(required = false) Map<String, String> request) {
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "generate-card"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "generate-card", "生成词汇卡", conversationId);
        log.info("冻结点数: {}，用于生成词汇卡", cost);
        
        try {
            String level = request != null ? request.get("level") : null;
            VocabularyCardDTO card = vocabularyCardService.generateNextCard(conversationId, level);
            balanceService.confirmTransaction(transactionId);
            log.info("确认扣费: {}，生成词汇卡成功", cost);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("新词汇卡生成成功", card));
        } catch (Exception e) {
            log.error("生成词汇卡失败，返还余额: {}", cost, e);
            balanceService.cancelTransaction(transactionId);
            throw e;
        }
    }

    /**
     * 重新生成当前词汇卡（删除当前未完成的，生成新的）
     * @param conversationId 对话ID
     * @param request 包含难度级别
     * @return 重新生成的词汇卡
     */
    @PostMapping("/conversations/{conversationId}/regenerate")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> regenerateCard(
            @PathVariable Long conversationId,
            @RequestBody(required = false) Map<String, String> request) {
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "regenerate-card"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "regenerate-card", "重新生成词汇卡", conversationId);
        log.info("冻结点数: {}，用于重新生成词汇卡", cost);
        
        try {
            String level = request != null ? request.get("level") : null;
            VocabularyCardDTO card = vocabularyCardService.regenerateCard(conversationId, level);
            balanceService.confirmTransaction(transactionId);
            log.info("确认扣费: {}，重新生成词汇卡成功", cost);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("词汇卡重新生成成功", card));
        } catch (Exception e) {
            log.error("重新生成词汇卡失败，返还余额: {}", cost, e);
            balanceService.cancelTransaction(transactionId);
            throw e;
        }
    }

    /**
     * 更新用户对单词的释义猜测
     * @param cardId 词汇卡ID
     * @param request 包含用户释义
     * @return 更新后的词汇卡
     */
    @PutMapping("/cards/{cardId}/meaning")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> updateUserMeaning(
            @PathVariable Long cardId,
            @RequestBody Map<String, String> request) {
        VocabularyCardDTO card = vocabularyCardService.getCardById(cardId);
        Long conversationId = card != null ? card.getConversationId() : null;
        
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "update-meaning"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "update-meaning", "释义检查", conversationId);
        log.info("冻结点数: {}，用于释义检查", cost);
        
        try {
            String userMeaning = request.get("userMeaning");
            VocabularyCardDTO updated = vocabularyCardService.updateUserMeaning(cardId, userMeaning);
            balanceService.confirmTransaction(transactionId);
            log.info("确认扣费: {}，释义检查成功", cost);
            return ResponseEntity.ok(ApiResponse.success("用户意思已更新", updated));
        } catch (Exception e) {
            log.error("释义检查失败，返还余额: {}", cost, e);
            balanceService.cancelTransaction(transactionId);
            throw e;
        }
    }

    /**
     * 更新用户用单词造的句子
     * @param cardId 词汇卡ID
     * @param request 包含用户造句
     * @return 更新后的词汇卡
     */
    @PutMapping("/cards/{cardId}/sentence")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> updateUserSentence(
            @PathVariable Long cardId,
            @RequestBody Map<String, String> request) {
        String userSentence = request.get("userSentence");
        VocabularyCardDTO updated = vocabularyCardService.updateUserSentence(cardId, userSentence);
        return ResponseEntity.ok(ApiResponse.success("用户造句已更新", updated));
    }

    /**
     * 更新AI对用户造句的反馈
     * @param cardId 词汇卡ID
     * @param request 包含AI反馈
     * @return 更新后的词汇卡
     */
    @PutMapping("/cards/{cardId}/feedback")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> updateAIFeedback(
            @PathVariable Long cardId,
            @RequestBody Map<String, String> request) {
        String feedback = request.get("feedback");
        VocabularyCardDTO updated = vocabularyCardService.updateAIFeedback(cardId, feedback);
        return ResponseEntity.ok(ApiResponse.success("AI反馈已更新", updated));
    }

    /**
     * 标记词汇卡为已完成
     * @param cardId 词汇卡ID
     * @return 更新后的词汇卡
     */
    @PutMapping("/cards/{cardId}/complete")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> markAsCompleted(
            @PathVariable Long cardId) {
        VocabularyCardDTO updated = vocabularyCardService.markAsCompleted(cardId);
        return ResponseEntity.ok(ApiResponse.success("词汇卡已标记为完成", updated));
    }

    /**
     * 删除对话的所有词汇卡
     * @param conversationId 对话ID
     * @return 删除成功响应
     */
    @DeleteMapping("/conversations/{conversationId}/cards")
    public ResponseEntity<Void> deleteAllCards(@PathVariable Long conversationId) {
        vocabularyCardService.deleteAllCards(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取对话的词汇卡数量
     * @param conversationId 对话ID
     * @return 词汇卡数量
     */
    @GetMapping("/conversations/{conversationId}/count")
    public ResponseEntity<ApiResponse<Long>> getCardCount(
            @PathVariable Long conversationId) {
        long count = vocabularyCardService.getCardCount(conversationId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * 获取词汇卡的释义检查状态（用于前端轮询异步检查结果）
     * @param cardId 词汇卡ID
     * @return 释义检查状态
     */
    @GetMapping("/cards/{cardId}/meaning-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMeaningCheckStatus(
            @PathVariable Long cardId) {
        VocabularyCardDTO card = vocabularyCardService.getCardById(cardId);
        Map<String, Object> status = new HashMap<>();
        status.put("cardId", card.getId());
        status.put("word", card.getWord() != null ? card.getWord() : "");
        status.put("userMeaningGuess", card.getUserMeaningGuess() != null ? card.getUserMeaningGuess() : "");
        status.put("meaningCheckCompleted", Boolean.TRUE.equals(card.getMeaningCheckCompleted()));
        status.put("meaningIsCorrect", card.getMeaningIsCorrect());
        status.put("meaningCheckResult", card.getMeaningCheckResult() != null ? card.getMeaningCheckResult() : "");
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
