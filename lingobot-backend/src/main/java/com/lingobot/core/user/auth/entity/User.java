package com.lingobot.core.user.auth.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.lingobot.core.conversation.entity.Conversation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体。
 *
 * 存储系统用户的核心信息，包括认证凭证、个人资料和角色权限。
 * 每个用户可以拥有多个对话，级联保存和删除。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    
    /**
     * 用户角色枚举。
     * ROLE_USER - 普通用户，拥有基础功能权限
     * ROLE_ADMIN - 管理员，拥有系统管理权限
     */
    public enum Role {
        ROLE_USER,
        ROLE_ADMIN
    }
    
    // 用户主键 ID，自增生成
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 用户名，唯一且不能为空，长度 3-20 字符
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    // 邮箱地址，唯一且不能为空，用于登录和验证码接收
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    // 加密后的密码，BCrypt 格式存储
    @Column(nullable = false, length = 255)
    private String password;
    
    // 用户角色，默认为普通用户
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.ROLE_USER;
    
    // 当前正在进行的对话 ID，用于快速定位用户的活跃对话
    @Column(name = "current_conversation_id")
    private Long currentConversationId;
    
    // 用户头像，Base64 编码存储
    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;
    
    // 账户创建时间，首次保存时自动设置
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 账户信息最后更新时间，每次更新时自动刷新
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // 用户拥有的对话列表，级联保存和删除
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Conversation> conversations = new ArrayList<>();
    
    // 实体持久化前自动设置创建时间、更新时间和默认角色
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (role == null) {
            role = Role.ROLE_USER;
        }
    }
    
    // 实体更新前自动刷新更新时间
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
