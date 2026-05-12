package com.lingobot.learning.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标准化单词实体类。
 *
 * 存储去重后的单词（归一化形式：小写 + trim），
 * 作为用户词汇学习的共享基础数据，避免同一单词被多个用户重复存储。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vocabulary_words", uniqueConstraints = {
        @UniqueConstraint(columnNames = "normalized_word")
})
public class VocabularyWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 归一化后的单词（trim + 小写，用于去重）
    @Column(name = "normalized_word", nullable = false, length = 100)
    private String normalizedWord;

    // 创建时间
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 持久化前自动设置创建时间
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
