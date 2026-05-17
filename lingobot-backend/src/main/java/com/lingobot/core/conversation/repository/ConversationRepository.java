package com.lingobot.core.conversation.repository;

import com.lingobot.core.conversation.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话数据访问层。
 *
 * 提供会话实体的数据库操作接口，
 * 支持按用户、分页、publicId 等多种方式查询会话。
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // 分页查询指定用户的会话
    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    // 分页查询所有会话
    Page<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    // 查询指定用户最近的 N 条会话（通过 Pageable 限制数量）
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    // 查询最近的 N 条会话（通过 Pageable 限制数量）
    List<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    // 根据 ID 和用户 ID 查询会话（用于权限校验）
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    // 根据 publicId 查询会话
    Optional<Conversation> findByPublicId(String publicId);

    // 根据 publicId 和用户 ID 查询会话（用于权限校验）
    Optional<Conversation> findByPublicIdAndUserId(String publicId, Long userId);
}
