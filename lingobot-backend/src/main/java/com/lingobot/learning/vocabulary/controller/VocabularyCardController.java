package com.lingobot.learning.vocabulary.controller;

import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.learning.vocabulary.dto.MeaningCheckStatusDTO;
import com.lingobot.learning.vocabulary.dto.SentenceAnalysisStatusDTO;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
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
 * 词汇卡管理控制器。
 *
 * 提供词汇卡的增删改查、导航、AI生成等功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyCardController {

    private final VocabularyCardService vocabularyCardService;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final BalanceService balanceService;
    private final ApiConfigProperties apiConfigProperties;
    private final ConversationService conversationService;

    private Long resolvePublicId(String publicId) {
        return conversationService.resolvePublicIdToId(publicId);
    }

    /**
     * 创建新的词汇卡
     * @param conversationPublicId 对话的publicId
     * @param request 词汇卡创建请求
     * @return 创建成功的词汇卡
     */
    @PostMapping("/cards")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> createCard(
            @RequestParam String conversationPublicId,
            @RequestBody CreateVocabularyCardRequest request) {
        Long conversationId = resolvePublicId(conversationPublicId);
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
     * @param conversationPublicId 对话的publicId
     * @return 词汇卡列表
     */
    @GetMapping("/conversations/{conversationPublicId}/cards")
    public ResponseEntity<ApiResponse<List<VocabularyCardDTO>>> getAllCards(
            @PathVariable String conversationPublicId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        List<VocabularyCardDTO> cards = vocabularyCardService.getAllCards(conversationId);
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    /**
     * 获取当前正在学习的词汇卡（第一个未完成的，或最后一个）
     * @param conversationPublicId 对话的publicId
     * @return 当前词汇卡
     */
    @GetMapping("/conversations/{conversationPublicId}/current")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getCurrentCard(
            @PathVariable String conversationPublicId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        VocabularyCardDTO card = vocabularyCardService.getCurrentCard(conversationId);
        if (card == null) {
            return ResponseEntity.ok(ApiResponse.success("该对话没有词汇卡", null));
        }
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    /**
     * 获取下一个词汇卡
     * @param conversationPublicId 对话的publicId
     * @param currentPosition 当前位置（可选）
     * @return 下一个词汇卡
     */
    @GetMapping("/conversations/{conversationPublicId}/next")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getNextCard(
            @PathVariable String conversationPublicId,
            @RequestParam(required = false) Integer currentPosition,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty) {
        try {
            Long conversationId = resolvePublicId(conversationPublicId);
            VocabularyCardDTO card = vocabularyCardService.getNextCard(conversationId, currentPosition, category, difficulty);
            return ResponseEntity.ok(ApiResponse.success(card));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(e.getMessage(), null));
        }
    }

    /**
     * 获取上一个词汇卡
     * @param conversationPublicId 对话的publicId
     * @param currentPosition 当前位置（可选）
     * @return 上一个词汇卡
     */
    @GetMapping("/conversations/{conversationPublicId}/prev")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> getPrevCard(
            @PathVariable String conversationPublicId,
            @RequestParam(required = false) Integer currentPosition) {
        try {
            Long conversationId = resolvePublicId(conversationPublicId);
            VocabularyCardDTO card = vocabularyCardService.getPrevCard(conversationId, currentPosition);
            return ResponseEntity.ok(ApiResponse.success(card));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(e.getMessage(), null));
        }
    }

    /**
     * 生成下一个词汇卡（通过AI生成新单词）
     * @param conversationPublicId 对话的publicId
     * @param request 包含词汇类别和难度级别
     * @return 生成的词汇卡
     */
    @PostMapping("/conversations/{conversationPublicId}/generate")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> generateNextCard(
            @PathVariable String conversationPublicId,
            @RequestBody(required = false) Map<String, String> request) {
        Long conversationId = resolvePublicId(conversationPublicId);
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "generate-card"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "generate-card", "生成词汇卡", conversationId);
        log.info("冻结点数: {}，用于生成词汇卡", cost);
        
        try {
            String category = request != null ? request.get("category") : null;
            String difficulty = request != null ? request.get("difficulty") : null;
            VocabularyCardDTO card = vocabularyCardService.generateNextCard(conversationId, category, difficulty);
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
     * @param conversationPublicId 对话的publicId
     * @param request 包含词汇类别和难度级别
     * @return 重新生成的词汇卡
     */
    @PostMapping("/conversations/{conversationPublicId}/regenerate")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> regenerateCard(
            @PathVariable String conversationPublicId,
            @RequestBody(required = false) Map<String, String> request) {
        Long conversationId = resolvePublicId(conversationPublicId);
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "regenerate-card"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "regenerate-card", "重新生成词汇卡", conversationId);
        log.info("冻结点数: {}，用于重新生成词汇卡", cost);
        
        try {
            String category = request != null ? request.get("category") : null;
            String difficulty = request != null ? request.get("difficulty") : null;
            VocabularyCardDTO card = vocabularyCardService.regenerateCard(conversationId, category, difficulty);
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
     * 更新用户根据中文例句写的英文句子
     * @param cardId 词汇卡ID
     * @param request 包含用户写的英文句子
     * @return 更新后的词汇卡
     */
    @PutMapping("/cards/{cardId}/english-sentence")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> updateUserEnglishSentence(
            @PathVariable Long cardId,
            @RequestBody Map<String, String> request) {
        String userEnglishSentence = request.get("userEnglishSentence");
        VocabularyCardDTO updated = vocabularyCardService.updateUserEnglishSentence(cardId, userEnglishSentence);
        return ResponseEntity.ok(ApiResponse.success("用户英文句子已更新", updated));
    }

    /**
     * 触发 AI 异步分析用户写的英文句子（收费接口）
     * @param cardId 词汇卡ID
     * @return 触发后的词汇卡（分析结果通过轮询 /sentence-analysis 获取）
     */
    @PostMapping("/cards/{cardId}/analyze-sentence")
    public ResponseEntity<ApiResponse<VocabularyCardDTO>> analyzeUserSentence(
            @PathVariable Long cardId) {
        VocabularyCardDTO card = vocabularyCardService.getCardById(cardId);
        Long conversationId = card != null ? card.getConversationId() : null;
        
        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("vocabulary", "analyze-sentence"));
        Long transactionId = balanceService.freezeBalance(cost, "vocabulary", "analyze-sentence", "句子分析", conversationId);
        log.info("冻结点数: {}，用于句子分析", cost);
        
        try {
            vocabularyCardService.analyzeUserSentenceAsync(cardId);
            balanceService.confirmTransaction(transactionId);
            log.info("确认扣费: {}，句子分析已触发", cost);
            VocabularyCardDTO updated = vocabularyCardService.getCardById(cardId);
            return ResponseEntity.ok(ApiResponse.success("句子分析已触发", updated));
        } catch (Exception e) {
            log.error("句子分析触发失败，返还余额: {}", cost, e);
            balanceService.cancelTransaction(transactionId);
            throw e;
        }
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
     * @param conversationPublicId 对话的publicId
     * @return 删除成功响应
     */
    @DeleteMapping("/conversations/{conversationPublicId}/cards")
    public ResponseEntity<Void> deleteAllCards(@PathVariable String conversationPublicId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        vocabularyCardService.deleteAllCards(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取对话的词汇卡数量
     * @param conversationPublicId 对话的publicId
     * @return 词汇卡数量
     */
    @GetMapping("/conversations/{conversationPublicId}/count")
    public ResponseEntity<ApiResponse<Long>> getCardCount(
            @PathVariable String conversationPublicId) {
        Long conversationId = resolvePublicId(conversationPublicId);
        long count = vocabularyCardService.getCardCount(conversationId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * 获取词汇卡的释义检查状态（用于前端轮询异步检查结果）
     * @param cardId 词汇卡ID
     * @return 释义检查状态
     */
    @GetMapping("/cards/{cardId}/meaning-check")
    public ResponseEntity<ApiResponse<MeaningCheckStatusDTO>> getMeaningCheckStatus(
            @PathVariable Long cardId) {
        // This endpoint is polled immediately after async native updates.
        // Use a native projection so the response reflects the database row, not a stale JPA entity.
        VocabularyCardRepository.MeaningCheckStatusProjection card = vocabularyCardRepository
                .findMeaningCheckStatusByCardId(cardId)
                .orElseThrow(() -> com.lingobot.infrastructure.common.exception.ChatException.badRequest("Vocabulary card not found: " + cardId));
        MeaningCheckStatusDTO status = MeaningCheckStatusDTO.builder()
                .cardId(card.getCardId())
                .word(card.getWord() != null ? card.getWord() : "")
                .userMeaningGuess(card.getUserMeaningGuess() != null ? card.getUserMeaningGuess() : "")
                .meaningCheckCompleted(Boolean.TRUE.equals(card.getMeaningCheckCompleted()))
                .meaningIsCorrect(card.getMeaningIsCorrect())
                .meaningCheckResult(card.getMeaningCheckResult() != null ? card.getMeaningCheckResult() : "")
                .chineseSentenceForTranslation(card.getChineseSentenceForTranslation() != null ? card.getChineseSentenceForTranslation() : "")
                .build();
        log.info("Meaning check status: cardId={}, completed={}, correct={}",
                status.getCardId(), status.getMeaningCheckCompleted(), status.getMeaningIsCorrect());
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * 获取句子分析状态（用于前端轮询异步分析结果）
     * @param cardId 词汇卡ID
     * @return 句子分析状态
     */
    @GetMapping("/cards/{cardId}/sentence-analysis")
    public ResponseEntity<ApiResponse<SentenceAnalysisStatusDTO>> getSentenceAnalysisStatus(
            @PathVariable Long cardId) {
        VocabularyCardDTO card = vocabularyCardService.getCardByIdFromDb(cardId);
        SentenceAnalysisStatusDTO status = SentenceAnalysisStatusDTO.builder()
                .cardId(card.getId())
                .word(card.getWord() != null ? card.getWord() : "")
                .chineseSentenceForTranslation(card.getChineseSentenceForTranslation() != null ? card.getChineseSentenceForTranslation() : "")
                .userEnglishSentence(card.getUserEnglishSentence() != null ? card.getUserEnglishSentence() : "")
                .sentenceAnalysisCompleted(Boolean.TRUE.equals(card.getSentenceAnalysisCompleted()))
                .sentenceHasNewWord(card.getSentenceHasNewWord())
                .sentenceMeaningMatches(card.getSentenceMeaningMatches())
                .sentenceAnalysis(card.getSentenceAnalysis() != null ? card.getSentenceAnalysis() : "")
                .build();
        log.info("Sentence analysis status: cardId={}, completed={}, hasNewWord={}, meaningMatches={}",
                status.getCardId(), status.getSentenceAnalysisCompleted(),
                status.getSentenceHasNewWord(), status.getSentenceMeaningMatches());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
