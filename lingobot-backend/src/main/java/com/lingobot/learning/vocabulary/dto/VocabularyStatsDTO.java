package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 词汇学习统计数据传输对象。
 *
 * 汇总用户词汇学习的各项统计指标，
 * 用于前端展示学习进度概览。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyStatsDTO {

    // 词汇总数
    private long totalCount;
    // 新词汇数（状态为 NEW）
    private long newCount;
    // 学习中词汇数（状态为 LEARNING）
    private long learningCount;
    // 复习中词汇数（状态为 REVIEWING）
    private long reviewingCount;
    // 已掌握词汇数（状态为 MASTERED）
    private long masteredCount;
    // 待复习词汇数（nextReviewAt <= 当前时间）
    private long toReviewCount;
    // 易错词汇数（掌握程度 <= 0.40）
    private long difficultCount;
}
