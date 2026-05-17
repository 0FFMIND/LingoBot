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

/**
 * 会话实体。
 *
 * 表示用户与 AI 之间的一次对话会话，
 * 是消息的容器，负责组织和管理一组相关的消息记录。
 *
 * 仅包含通用会话字段，学习相关数据（学习模式、词汇意图等）
 * 存储在 ConversationLearningData 实体中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation {

    // 数据库主键，自增
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 对外暴露的唯一标识，用于 API 接口，避免暴露数据库自增 ID
    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private String publicId;

    // 会话标题，用于在列表中展示
    @Column(nullable = false)
    private String title;

    // 创建时间，不可修改
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 最后更新时间，每次添加消息或修改标题时更新
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 关联的用户，未登录用户创建的会话可以为空
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private com.lingobot.core.user.auth.entity.User user;

    // 会话包含的消息列表，级联保存、删除 orphanRemoval = true 表示从列表移除的消息会被删除
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<Message> messages = new ArrayList<>();

    // 实体持久化前自动设置创建时间、更新时间、publicId、默认标题
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
    }

    // 实体更新前自动更新 updatedAt
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 向会话添加消息，同时自动更新 updatedAt
    public void addMessage(Message message) {
        messages.add(message);
        message.setConversation(this);
        this.updatedAt = LocalDateTime.now();
    }
}
