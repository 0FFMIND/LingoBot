package com.lingobot.core.user.preference.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户偏好设置实体。
 *
 * 存储用户的个性化设置，包括词汇学习偏好（划分标准、难度、模型）
 * 和聊天模型偏好。每个用户对应一条记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_preferences", uniqueConstraints = {
    @UniqueConstraint(columnNames = "user_id")
})
public class UserPreference {

    // 主键 ID，自增生成
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联的用户，一对一关系
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 词汇划分标准：cefr（欧洲语言共同参考框架）、ielts（雅思）、toefl（托福）
    @Column(name = "vocabulary_category", length = 20)
    @Builder.Default
    private String vocabularyCategory = "cefr";

    // 词汇难度级别：根据不同的划分标准有不同的取值
    @Column(name = "vocabulary_difficulty", length = 20)
    @Builder.Default
    private String vocabularyDifficulty = "b2";

    // 词汇学习使用的 AI 模型
    @Column(name = "vocabulary_model", length = 50)
    @Builder.Default
    private String vocabularyModel = "qwen";

    // 聊天使用的 AI 模型
    @Column(name = "chat_model", length = 50)
    @Builder.Default
    private String chatModel = "qwen";

    // 创建时间，首次保存时自动设置
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新时间，每次更新时自动刷新
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 实体持久化前自动设置创建时间、更新时间和默认值
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (vocabularyCategory == null) {
            vocabularyCategory = "cefr";
        }
        if (vocabularyDifficulty == null) {
            vocabularyDifficulty = "b2";
        }
        if (vocabularyModel == null) {
            vocabularyModel = "qwen";
        }
        if (chatModel == null) {
            chatModel = "qwen";
        }
    }

    // 实体更新前自动刷新更新时间
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
