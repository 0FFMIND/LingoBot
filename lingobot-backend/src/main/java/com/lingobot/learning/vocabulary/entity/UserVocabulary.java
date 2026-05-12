package com.lingobot.learning.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户词汇实体类。
 *
 * 记录用户对每个标准化单词的学习进度、掌握程度和复习计划。
 * 每个用户对每个单词只能有一条记录（通过 user_id + vocabulary_word_id 唯一约束）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_vocabularies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "vocabulary_word_id"})
})
public class UserVocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 关联的标准化单词ID
    @Column(name = "vocabulary_word_id", nullable = false)
    private Long vocabularyWordId;

    // 学习状态（NEW/LEARNING/REVIEWING/MASTERED/IGNORED）
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VocabularyStatus status = VocabularyStatus.NEW;

    // 掌握程度得分（0.00-1.00，根据正确/错误次数计算）
    @Column(name = "mastery_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal masteryScore = BigDecimal.ZERO;

    // 已见次数（用户看到这个单词的总次数）
    @Column(name = "seen_count", nullable = false)
    @Builder.Default
    private Integer seenCount = 0;

    // 正确次数（测验中答对的次数）
    @Column(name = "correct_count", nullable = false)
    @Builder.Default
    private Integer correctCount = 0;

    // 错误次数（测验中答错的次数）
    @Column(name = "wrong_count", nullable = false)
    @Builder.Default
    private Integer wrongCount = 0;

    // 首次见时间
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    // 最近见时间
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // 下次复习时间（根据艾宾浩斯遗忘曲线计算）
    @Column(name = "next_review_at")
    private LocalDateTime nextReviewAt;

    // 最后事件类型（NEW_LEARNING/REVIEW/HYBRID）
    @Enumerated(EnumType.STRING)
    @Column(name = "last_event_type", length = 20)
    private VocabularyEventType lastEventType;

    // 创建时间
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新时间
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 持久化前自动设置创建时间和默认值
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (seenCount == null) {
            seenCount = 0;
        }
        if (correctCount == null) {
            correctCount = 0;
        }
        if (wrongCount == null) {
            wrongCount = 0;
        }
        if (masteryScore == null) {
            masteryScore = BigDecimal.ZERO;
        }
        if (status == null) {
            status = VocabularyStatus.NEW;
        }
    }

    // 更新前自动设置更新时间
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
