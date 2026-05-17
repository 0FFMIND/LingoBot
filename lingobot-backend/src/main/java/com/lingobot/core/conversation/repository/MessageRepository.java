package com.lingobot.core.conversation.repository;

import com.lingobot.core.conversation.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 消息数据访问层。
 *
 * 提供消息实体的数据库操作接口，
 * 支持按会话查询消息、删除消息、统计消息数量等操作。
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 查询指定会话的所有消息，按时间升序排列
    List<Message> findByConversationIdOrderByTimestampAsc(Long conversationId);

    // 查询指定会话最近的 10 条消息，按时间倒序排列
    List<Message> findTop10ByConversationIdOrderByTimestampDesc(Long conversationId);

    // 查询指定会话中指定角色的最新一条消息
    Optional<Message> findFirstByConversationIdAndRoleOrderByTimestampDesc(Long conversationId, String role);

    // 查询指定会话的所有消息，按时间倒序排列（用于获取最近 N 条消息）
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.timestamp DESC")
    List<Message> findLastMessagesByConversationId(@Param("conversationId") Long conversationId);

    // 统计指定会话的消息总数
    int countByConversationId(Long conversationId);

    // 根据 ID 删除单条消息
    @Modifying
    @Query("DELETE FROM Message m WHERE m.id = :id")
    void deleteMessageById(@Param("id") Long id);

    // 批量删除多条消息
    @Modifying
    @Query("DELETE FROM Message m WHERE m.id IN :ids")
    void deleteMessagesByIds(@Param("ids") List<Long> ids);
}
