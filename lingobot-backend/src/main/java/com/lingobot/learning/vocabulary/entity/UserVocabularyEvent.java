package com.lingobot.learning.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_vocabulary_events", indexes = {
        @Index(name = "idx_event_user_id", columnList = "user_id"),
        @Index(name = "idx_event_vocabulary_word_id", columnList = "vocabulary_word_id"),
        @Index(name = "idx_event_user_vocabulary_id", columnList = "user_vocabulary_id"),
        @Index(name = "idx_event_vocabulary_card_id", columnList = "vocabulary_card_id"),
        @Index(name = "idx_event_created_at", columnList = "created_at")
})
public class UserVocabularyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vocabulary_word_id")
    private Long vocabularyWordId;

    @Column(name = "user_vocabulary_id")
    private Long userVocabularyId;

    @Column(name = "vocabulary_card_id")
    private Long vocabularyCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LearningEventType eventType;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "score_delta", precision = 5, scale = 2)
    private BigDecimal scoreDelta;

    @Column(name = "mastery_score_before", precision = 5, scale = 2)
    private BigDecimal masteryScoreBefore;

    @Column(name = "mastery_score_after", precision = 5, scale = 2)
    private BigDecimal masteryScoreAfter;

    @Column(name = "meaning_user_answer", columnDefinition = "TEXT")
    private String meaningUserAnswer;

    @Column(name = "meaning_is_correct")
    private Boolean meaningIsCorrect;

    @Column(name = "meaning_feedback", columnDefinition = "TEXT")
    private String meaningFeedback;

    @Column(name = "sentence_user_answer", columnDefinition = "TEXT")
    private String sentenceUserAnswer;

    @Column(name = "sentence_has_new_word")
    private Boolean sentenceHasNewWord;

    @Column(name = "sentence_meaning_matches")
    private Boolean sentenceMeaningMatches;

    @Column(name = "sentence_feedback", columnDefinition = "TEXT")
    private String sentenceFeedback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
