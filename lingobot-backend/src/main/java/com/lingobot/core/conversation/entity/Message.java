package com.lingobot.core.conversation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体。
 *
 * 表示对话中的单条消息，包含文本、音频、图片等多种类型，
 * 记录发送者角色（用户/AI助手）、token 使用量等信息。
 *
 * 每条消息必须属于一个 Conversation，支持文本、音频、图片三种消息类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages")
public class Message {

    // 文本消息类型
    public static final String MESSAGE_TYPE_TEXT = "text";
    // 音频消息类型
    public static final String MESSAGE_TYPE_AUDIO = "audio";
    // 图片消息类型
    public static final String MESSAGE_TYPE_IMAGE = "image";

    // 数据库主键，自增
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 消息内容，支持长文本
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 消息发送者角色：user（用户）或 assistant（AI 助手）
    @Column(nullable = false, length = 20)
    private String role;

    // 消息类型：text/audio/image，默认为 text
    @Column(name = "message_type", length = 20)
    @Builder.Default
    private String messageType = MESSAGE_TYPE_TEXT;

    // 音频数据，Base64 编码
    @Column(name = "audio_data", columnDefinition = "TEXT")
    private String audioData;

    // 音频格式，如 mp3、wav 等
    @Column(name = "audio_format", length = 20)
    private String audioFormat;

    // 音频时长，单位秒
    @Column(name = "audio_duration")
    private Integer audioDuration;

    // 图片数据，Base64 编码
    @Column(name = "image_data", columnDefinition = "TEXT")
    private String imageData;

    // 图片格式，如 png、jpeg 等
    @Column(name = "image_format", length = 16)
    private String imageFormat;

    // LLM 输入 token 数
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    // LLM 输出 token 数
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    // 总 token 数
    @Column(name = "total_tokens")
    private Integer totalTokens;

    // 消息发送时间，不可修改
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // 所属会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // 实体持久化前自动设置时间戳和默认消息类型
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if (messageType == null) {
            messageType = MESSAGE_TYPE_TEXT;
        }
    }
}
