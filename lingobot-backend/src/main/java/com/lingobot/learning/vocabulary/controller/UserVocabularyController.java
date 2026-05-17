package com.lingobot.learning.vocabulary.controller;

import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.config.ApiConfigProperties;
import com.lingobot.infrastructure.common.response.PageResponseDTO;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.vocabulary.dto.AIModifyVocabularyRequest;
import com.lingobot.learning.vocabulary.dto.UpdateUserVocabularyRequest;
import com.lingobot.learning.vocabulary.dto.UpdateLearningStateRequest;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.dto.VocabularyStatsDTO;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.service.VocabularyAIModifyService;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户词汇管理控制器。
 *
 * 提供用户词汇本的管理接口，包括学习统计查询、词汇列表分页查询、
 * 手动更新词汇信息、AI智能完善、删除词汇等功能。
 *
 * 所有接口均在 /api/user-vocabulary 路径下，
 * 返回统一的 ApiResponse<T> 响应格式。
 */
@Slf4j
@RestController
@RequestMapping("/api/user-vocabulary")
@RequiredArgsConstructor
public class UserVocabularyController {

    private final UserVocabularyService userVocabularyService;
    private final AuthService authService;
    private final VocabularyAIModifyService vocabularyAIModifyService;
    private final BalanceService balanceService;
    private final ApiConfigProperties apiConfigProperties;

    // 获取用户词汇学习统计
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<VocabularyStatsDTO>> getStats() {
        Long userId = authService.getCurrentUserId();
        VocabularyStatsDTO stats = userVocabularyService.getStats(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // 分页查询用户词汇列表（支持状态筛选、类型筛选、排序和搜索）
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<PageResponseDTO<UserVocabularyDTO>>> getVocabularies(
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false, defaultValue = "last_seen") String sortBy,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Long userId = authService.getCurrentUserId();
        PageResponseDTO<UserVocabularyDTO> result = userVocabularyService.getUserVocabularies(
                userId,
                status,
                filterType,
                sortBy,
                search,
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 忽略词汇（将学习状态标记为 UNKNOWN，不再参与学习和复习）
    @PutMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreVocabulary(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        log.info("Ignoring vocabulary: userId={}, id={}", userId, id);
        return ResponseEntity.ok(ApiResponse.success("Operation succeeded", null));
    }

    // 手动更新用户词汇信息（单词、音标、释义、同义词等）
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserVocabularyDTO>> updateVocabulary(
            @PathVariable Long id,
            @RequestBody UpdateUserVocabularyRequest request) {
        Long userId = authService.getCurrentUserId();
        UserVocabularyDTO updated = userVocabularyService.updateVocabulary(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Vocabulary updated", updated));
    }

    // 手动更新学习状态（状态、掌握度、下次复习时间）
    @PutMapping("/{id}/learning-state")
    public ResponseEntity<ApiResponse<UserVocabularyDTO>> updateLearningState(
            @PathVariable Long id,
            @RequestBody UpdateLearningStateRequest request) {
        Long userId = authService.getCurrentUserId();
        UserVocabularyDTO updated = userVocabularyService.updateLearningState(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Learning state updated", updated));
    }

    // AI 智能完善词汇信息（补充缺失字段、校验类别难度），收费接口
    @PostMapping("/ai-modify")
    public ResponseEntity<ApiResponse<UserVocabularyDTO>> aiModifyVocabulary(
            @RequestBody AIModifyVocabularyRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success("Please login first", null));
        }

        BigDecimal cost = BigDecimal.valueOf(apiConfigProperties.getCost("user-vocabulary", "ai-modify"));
        Long transactionId = balanceService.freezeBalance(cost, "user-vocabulary", "ai-modify", "AI完善词汇信息", null);
        try {
            UserVocabularyDTO updated = vocabularyAIModifyService.modifyWithAI(request);
            balanceService.confirmTransaction(transactionId);
            return ResponseEntity.ok(ApiResponse.success("AI modify succeeded", updated));
        } catch (RuntimeException e) {
            balanceService.cancelTransaction(transactionId);
            throw e;
        }
    }

    // 删除用户词汇记录（仅删除当前登录用户自己的数据）
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVocabulary(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        if (userId != null) {
            userVocabularyService.deleteVocabulary(userId, id);
        }
        return ResponseEntity.noContent().build();
    }

    private VocabularyStatsDTO emptyStats() {
        return VocabularyStatsDTO.builder()
                .totalCount(0)
                .newCount(0)
                .learningCount(0)
                .reviewingCount(0)
                .masteredCount(0)
                .toReviewCount(0)
                .build();
    }
}
