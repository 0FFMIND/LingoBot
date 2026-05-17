package com.lingobot.learning.conversation.chat.entity;

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
@Table(name = "chat_conversation_data")
public class ChatConversationData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", unique = true, nullable = false)
    private Long conversationId;

    @Column(name = "compacted_summary", columnDefinition = "TEXT")
    private String compactedSummary;

    @Column(name = "compacted_count")
    @Builder.Default
    private Integer compactedCount = 0;

    @Column(name = "last_compacted_at")
    private LocalDateTime lastCompactedAt;

    @Column(name = "total_tokens_estimate")
    @Builder.Default
    private Long totalTokensEstimate = 0L;
}
