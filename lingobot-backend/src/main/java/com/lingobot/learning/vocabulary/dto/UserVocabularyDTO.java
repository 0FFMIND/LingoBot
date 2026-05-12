package com.lingobot.learning.vocabulary.dto;

import com.lingobot.learning.vocabulary.entity.VocabularyEventType;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户词汇数据传输对象。
 *
 * 将 UserVocabulary 实体转换为前端友好格式，
 * 包含学习进度、掌握程度、复习计划等用户个人词汇信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVocabularyDTO {

    // 记录ID
    private Long id;
    // 用户ID
    private Long userId;
    // 标准化单词ID
    private Long vocabularyWordId;
    // 单词（从最新词汇卡获取）
    private String word;
    // 音标（从最新词汇卡获取）
    private String phonetic;
    // 释义（从最新词汇卡获取）
    private String meaning;
    // 词汇类别（从最新词汇卡获取）
    private String category;
    // 难度级别（从最新词汇卡获取）
    private String difficulty;
    // 学习状态（NEW/LEARNING/REVIEWING/MASTERED/IGNORED）
    private VocabularyStatus status;
    // 掌握程度得分（0.00-1.00）
    private BigDecimal masteryScore;
    // 已见次数
    private Integer seenCount;
    // 正确次数
    private Integer correctCount;
    // 错误次数
    private Integer wrongCount;
    // 首次见时间
    private LocalDateTime firstSeenAt;
    // 最近见时间
    private LocalDateTime lastSeenAt;
    // 下次复习时间
    private LocalDateTime nextReviewAt;
    // 最后事件类型（NEW_LEARNING/REVIEW/HYBRID）
    private VocabularyEventType lastEventType;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
