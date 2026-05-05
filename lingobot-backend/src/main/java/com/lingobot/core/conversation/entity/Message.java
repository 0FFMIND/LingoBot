package com.lingobot.core.conversation.entity;

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
@Table(name = "messages")
public class Message {
    
    public static final String MESSAGE_TYPE_TEXT = "text";
    public static final String MESSAGE_TYPE_AUDIO = "audio";
    public static final String MESSAGE_TYPE_IMAGE = "image";
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false, length = 20)
    private String role;
    
    @Column(name = "message_type", length = 20)
    @Builder.Default
    private String messageType = MESSAGE_TYPE_TEXT;
    
    @Column(name = "audio_data", columnDefinition = "TEXT")
    private String audioData;
    
    @Column(name = "audio_format", length = 20)
    private String audioFormat;
    
    @Column(name = "audio_duration")
    private Integer audioDuration;
    
    @Column(name = "image_data", columnDefinition = "TEXT")
    private String imageData;
    
    @Column(name = "image_format", length = 20)
    private String imageFormat;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (messageType == null) {
            messageType = MESSAGE_TYPE_TEXT;
        }
    }
}
