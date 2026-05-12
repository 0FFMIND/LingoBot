package com.lingobot.learning.vocabulary.service.impl;

import com.lingobot.infrastructure.common.dto.PageResponseDTO;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.dto.VocabularyStatsDTO;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyEventType;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户词汇服务实现类。
 *
 * 实现用户词汇学习进度的管理逻辑，
 * 包括学习记录的创建/更新、掌握程度计算和复习时间调度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserVocabularyServiceImpl implements UserVocabularyService {

    private final UserVocabularyRepository userVocabularyRepository;
    private final VocabularyCardRepository vocabularyCardRepository;

    // 新增或更新用户学习记录（默认事件类型为 NEW_LEARNING）
    @Override
    @Transactional
    public UserVocabulary upsertProgress(Long userId, Long vocabularyWordId) {
        return upsertProgress(userId, vocabularyWordId, VocabularyEventType.NEW_LEARNING);
    }

    // 新增或更新用户学习记录（指定事件类型）
    @Override
    @Transactional
    public UserVocabulary upsertProgress(Long userId, Long vocabularyWordId, VocabularyEventType eventType) {
        return userVocabularyRepository.findByUserIdAndVocabularyWordId(userId, vocabularyWordId)
                .map(existing -> {
                    existing.setSeenCount(existing.getSeenCount() + 1);
                    existing.setLastSeenAt(LocalDateTime.now());
                    existing.setLastEventType(eventType);
                    
                    if (existing.getStatus() == VocabularyStatus.NEW) {
                        existing.setStatus(VocabularyStatus.LEARNING);
                    }
                    
                    if (existing.getNextReviewAt() == null) {
                        existing.setNextReviewAt(LocalDateTime.now().plusDays(1));
                    }
                    
                    return userVocabularyRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("Creating new UserVocabulary for userId={}, vocabularyWordId={}", userId, vocabularyWordId);
                    UserVocabulary newProgress = UserVocabulary.builder()
                            .userId(userId)
                            .vocabularyWordId(vocabularyWordId)
                            .status(VocabularyStatus.NEW)
                            .masteryScore(BigDecimal.ZERO)
                            .seenCount(1)
                            .correctCount(0)
                            .wrongCount(0)
                            .firstSeenAt(LocalDateTime.now())
                            .lastSeenAt(LocalDateTime.now())
                            .lastEventType(eventType)
                            .nextReviewAt(LocalDateTime.now().plusDays(1))
                            .build();
                    return userVocabularyRepository.save(newProgress);
                });
    }

    // 更新用户学习进度（根据测验结果计算掌握程度和复习时间）
    @Override
    @Transactional
    public UserVocabulary updateProgress(Long userId, Long vocabularyWordId, boolean isCorrect) {
        return userVocabularyRepository.findByUserIdAndVocabularyWordId(userId, vocabularyWordId)
                .map(progress -> {
                    progress.setLastEventType(VocabularyEventType.HYBRID);
                    progress.setLastSeenAt(LocalDateTime.now());
                    if (isCorrect) {
                        progress.setCorrectCount(progress.getCorrectCount() + 1);
                    } else {
                        progress.setWrongCount(progress.getWrongCount() + 1);
                    }
                    
                    int totalAttempts = progress.getCorrectCount() + progress.getWrongCount();
                    if (totalAttempts > 0) {
                        BigDecimal newScore = BigDecimal.valueOf(progress.getCorrectCount())
                                .divide(BigDecimal.valueOf(totalAttempts), 2, RoundingMode.HALF_UP);
                        progress.setMasteryScore(newScore);
                        
                        if (newScore.compareTo(new BigDecimal("0.90")) >= 0) {
                            progress.setStatus(VocabularyStatus.MASTERED);
                        } else if (newScore.compareTo(new BigDecimal("0.60")) >= 0) {
                            progress.setStatus(VocabularyStatus.REVIEWING);
                        } else if (progress.getSeenCount() > 1) {
                            progress.setStatus(VocabularyStatus.LEARNING);
                        }
                    }

                    progress.setNextReviewAt(calculateNextReviewAt(progress.getMasteryScore(), isCorrect));
                    
                    return userVocabularyRepository.save(progress);
                })
                .orElseThrow(() -> new RuntimeException(
                        "UserVocabulary not found for userId=" + userId + ", vocabularyWordId=" + vocabularyWordId));
    }

    // 根据掌握程度和测验结果计算下次复习时间（模拟艾宾浩斯遗忘曲线）
    private LocalDateTime calculateNextReviewAt(BigDecimal masteryScore, boolean isCorrect) {
        LocalDateTime now = LocalDateTime.now();
        if (!isCorrect) {
            return now.plusHours(4);
        }
        if (masteryScore.compareTo(new BigDecimal("0.90")) >= 0) {
            return now.plusDays(14);
        }
        if (masteryScore.compareTo(new BigDecimal("0.60")) >= 0) {
            return now.plusDays(3);
        }
        return now.plusDays(1);
    }

    // 获取用户词汇学习统计数据
    @Override
    @Transactional(readOnly = true)
    public VocabularyStatsDTO getStats(Long userId) {
        return VocabularyStatsDTO.builder()
                .totalCount(userVocabularyRepository.countByUserId(userId))
                .newCount(userVocabularyRepository.countByUserIdAndStatus(userId, VocabularyStatus.NEW))
                .learningCount(userVocabularyRepository.countByUserIdAndStatus(userId, VocabularyStatus.LEARNING))
                .reviewingCount(userVocabularyRepository.countByUserIdAndStatus(userId, VocabularyStatus.REVIEWING))
                .masteredCount(userVocabularyRepository.countByUserIdAndStatus(userId, VocabularyStatus.MASTERED))
                .ignoredCount(userVocabularyRepository.countByUserIdAndStatus(userId, VocabularyStatus.IGNORED))
                .toReviewCount(userVocabularyRepository.countToReviewByUserId(userId, LocalDateTime.now()))
                .build();
    }

    // 分页查询用户词汇列表（支持状态筛选、类型筛选和排序）
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserVocabularyDTO> getUserVocabularies(
            Long userId,
            VocabularyStatus status,
            String filterType,
            String sortBy,
            int page,
            int size) {
        
        Sort sort = getSort(sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<UserVocabulary> vocabularies;
        
        if ("to_review".equals(filterType)) {
            vocabularies = userVocabularyRepository.findToReviewByUserId(userId, LocalDateTime.now(), pageable);
        } else if ("difficult".equals(filterType)) {
            vocabularies = userVocabularyRepository.findDifficultByUserId(userId, pageable);
        } else if (status != null) {
            vocabularies = userVocabularyRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            vocabularies = userVocabularyRepository.findByUserId(userId, pageable);
        }
        
        List<UserVocabularyDTO> dtoList = vocabularies.getContent().stream()
                .map(uv -> toDTO(userId, uv))
                .collect(Collectors.toList());
        
        return PageResponseDTO.of(
                dtoList,
                vocabularies.getNumber(),
                vocabularies.getSize(),
                vocabularies.getTotalElements()
        );
    }

    // 根据排序参数构建 Sort 对象
    private Sort getSort(String sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.DESC, "lastSeenAt");
        }
        return switch (sortBy) {
            case "first_seen" -> Sort.by(Sort.Direction.ASC, "firstSeenAt");
            case "last_seen" -> Sort.by(Sort.Direction.DESC, "lastSeenAt");
            case "mastery_asc" -> Sort.by(Sort.Direction.ASC, "masteryScore");
            case "mastery_desc" -> Sort.by(Sort.Direction.DESC, "masteryScore");
            case "seen_count" -> Sort.by(Sort.Direction.DESC, "seenCount");
            case "wrong_count" -> Sort.by(Sort.Direction.DESC, "wrongCount");
            case "next_review" -> Sort.by(Sort.Direction.ASC, "nextReviewAt");
            default -> Sort.by(Sort.Direction.DESC, "lastSeenAt");
        };
    }

    // 将 UserVocabulary 实体转换为 DTO（关联查询最新词汇卡获取单词详情）
    private UserVocabularyDTO toDTO(Long userId, UserVocabulary uv) {
        // 同一个单词可能有多张历史卡片，这里只取最新一张用于展示字段。
        List<VocabularyCard> latestCards = vocabularyCardRepository
                .findLatestCardsByUserIdAndVocabularyWordId(
                        userId,
                        uv.getVocabularyWordId(),
                        PageRequest.of(0, 1));
        VocabularyCard latestCard = latestCards.isEmpty() ? null : latestCards.get(0);
        
        return UserVocabularyDTO.builder()
                .id(uv.getId())
                .userId(uv.getUserId())
                .vocabularyWordId(uv.getVocabularyWordId())
                .word(latestCard != null ? latestCard.getWord() : null)
                .phonetic(latestCard != null ? latestCard.getPhonetic() : null)
                .meaning(latestCard != null ? latestCard.getMeaning() : null)
                .category(latestCard != null ? latestCard.getCategory() : null)
                .difficulty(latestCard != null ? latestCard.getDifficulty() : null)
                .status(uv.getStatus())
                .masteryScore(uv.getMasteryScore())
                .seenCount(uv.getSeenCount())
                .correctCount(uv.getCorrectCount())
                .wrongCount(uv.getWrongCount())
                .firstSeenAt(uv.getFirstSeenAt())
                .lastSeenAt(uv.getLastSeenAt())
                .nextReviewAt(uv.getNextReviewAt())
                .lastEventType(uv.getLastEventType())
                .createdAt(uv.getCreatedAt())
                .updatedAt(uv.getUpdatedAt())
                .build();
    }
}
