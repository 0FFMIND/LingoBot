package com.lingobot.core.user.preference.entity;

import com.lingobot.core.user.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_preferences", uniqueConstraints = {
    @UniqueConstraint(columnNames = "user_id")
})
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "vocabulary_category", length = 20)
    @Builder.Default
    private String vocabularyCategory = "cefr";

    @Column(name = "vocabulary_difficulty", length = 20)
    @Builder.Default
    private String vocabularyDifficulty = "b2";

    @Column(name = "vocabulary_model", length = 50)
    @Builder.Default
    private String vocabularyModel = "qwen";

    @Column(name = "chat_model", length = 50)
    @Builder.Default
    private String chatModel = "qwen";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
