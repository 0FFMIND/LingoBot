package com.lingobot.core.conversation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private String publicId;
    
    @Column(nullable = false)
    private String title;
    
    @Column(name = "learning_mode", length = 50)
    private String learningMode;
    
    @Column(name = "vocabulary_intent", length = 50)
    private String vocabularyIntent;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private com.lingobot.core.user.auth.entity.User user;
    
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<Message> messages = new ArrayList<>();
    
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (title == null || title.trim().isEmpty()) {
            title = "新对话" + createdAt.toLocalDate();
        }
        if (learningMode == null) {
            learningMode = "chat";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addMessage(Message message) {
        messages.add(message);
        message.setConversation(this);
        this.updatedAt = LocalDateTime.now();
    }
}
