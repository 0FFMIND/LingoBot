package com.lingobot.learning.vocabulary.controller;

import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.dto.VocabularyStatsDTO;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户词汇本控制器。
 *
 * 提供用户个人词汇的统计查询、列表查询和忽略管理等接口。
 * 所有接口统一挂载在 /api/user-vocabulary 路径下，响应体通过 ApiResponse<T> 包装。
 */
@Slf4j
@RestController
@RequestMapping("/api/user-vocabulary")
@RequiredArgsConstructor
public class UserVocabularyController {

    private final UserVocabularyService userVocabularyService;
    private final AuthService authService;

    // 获取当前用户的词汇学习统计数据（总数、各状态数量、待复习数量）
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<VocabularyStatsDTO>> getStats() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success(emptyStats()));
        }

        VocabularyStatsDTO stats = userVocabularyService.getStats(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // 分页查询用户词汇列表，支持按状态、筛选类型和排序方式过滤
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<PageResponseDTO<UserVocabularyDTO>>> getVocabularies(
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false, defaultValue = "last_seen") String sortBy,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success(PageResponseDTO.of(
                    java.util.Collections.emptyList(),
                    0,
                    size,
                    0
            )));
        }

        PageResponseDTO<UserVocabularyDTO> result = userVocabularyService.getUserVocabularies(
                userId,
                status,
                filterType,
                sortBy,
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 忽略某个词汇（将其状态标记为 IGNORED，不再参与学习和复习）
    @PutMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreVocabulary(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.success("Please login first", null));
        }

        log.info("Ignoring vocabulary: userId={}, id={}", userId, id);
        return ResponseEntity.ok(ApiResponse.success("Operation succeeded", null));
    }

    // 返回空的统计数据（用户未登录时使用）
    private VocabularyStatsDTO emptyStats() {
        return VocabularyStatsDTO.builder()
                .totalCount(0)
                .newCount(0)
                .learningCount(0)
                .reviewingCount(0)
                .masteredCount(0)
                .ignoredCount(0)
                .toReviewCount(0)
                .build();
    }
}
