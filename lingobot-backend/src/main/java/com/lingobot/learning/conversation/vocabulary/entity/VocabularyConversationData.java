package com.lingobot.learning.conversation.vocabulary.entity;

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
@Table(name = "vocabulary_conversation_data")
public class VocabularyConversationData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", unique = true, nullable = false)
    private Long conversationId;

    @Column(name = "vocabulary_intent", length = 50)
    private String vocabularyIntent;

    @Column(name = "vocabulary_compacted_summary", columnDefinition = "TEXT")
    private String vocabularyCompactedSummary;

    @Column(name = "vocabulary_last_compacted_card_id")
    private Long vocabularyLastCompactedCardId;

    @Column(name = "vocabulary_last_compacted_position")
    private Integer vocabularyLastCompactedPosition;

    @Column(name = "vocabulary_last_compacted_at")
    private LocalDateTime vocabularyLastCompactedAt;

    @Column(name = "vocabulary_compacted_card_count")
    @Builder.Default
    private Integer vocabularyCompactedCardCount = 0;

    @Column(name = "last_viewed_position")
    private Integer lastViewedPosition;
}
